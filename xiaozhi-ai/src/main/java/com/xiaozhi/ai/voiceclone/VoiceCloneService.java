package com.xiaozhi.ai.voiceclone;

import com.xiaozhi.ai.tts.TtsServiceFactory;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.config.service.ConfigService;
import com.xiaozhi.personal.dal.PersonalFeatureMapper;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VoiceCloneService {

    private static final long MAX_SAMPLE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of("audio/wav", "audio/x-wav", "audio/mpeg",
            "audio/mp3", "audio/mp4", "audio/m4a");

    private final PersonalFeatureMapper mapper;
    private final ConfigService configService;
    private final RuntimePathConfig runtimePathConfig;
    private final TtsServiceFactory ttsServiceFactory;
    private final Map<String, VoiceCloneProvider> providers;

    public VoiceCloneService(PersonalFeatureMapper mapper, ConfigService configService,
                             RuntimePathConfig runtimePathConfig, TtsServiceFactory ttsServiceFactory,
                             List<VoiceCloneProvider> providers) {
        this.mapper = mapper;
        this.configService = configService;
        this.runtimePathConfig = runtimePathConfig;
        this.ttsServiceFactory = ttsServiceFactory;
        this.providers = providers.stream().collect(Collectors.toMap(VoiceCloneProvider::provider, Function.identity()));
    }

    public VoiceClone create(Integer userId, Integer configId, String name, String mimeType, byte[] sample,
                             String sourceUrl, String requestedVoiceId, String sampleText) {
        if (userId == null || configId == null || !StringUtils.hasText(name) || sample == null || sample.length == 0) {
            throw new IllegalArgumentException("用户、配置、音色名称和样本不能为空");
        }
        if (sample.length > MAX_SAMPLE_SIZE || !ALLOWED_MIME.contains(mimeType)) {
            throw new IllegalArgumentException("音频必须是 WAV/MP3/M4A 且不超过 20MB");
        }
        ConfigBO config = requireConfig(userId, configId);
        VoiceCloneProvider provider = requireProvider(config.getProvider());
        Path userDir = runtimePathConfig.resolveVoiceSampleDir().resolve(String.valueOf(userId)).normalize();
        ensureWithin(runtimePathConfig.resolveVoiceSampleDir(), userDir);
        try {
            Files.createDirectories(userDir);
            String extension = extension(mimeType);
            Path target = userDir.resolve(UUID.randomUUID() + extension).normalize();
            ensureWithin(userDir, target);
            Files.write(target, sample);

            VoiceClone clone = new VoiceClone();
            clone.setUserId(userId);
            clone.setConfigId(configId);
            clone.setProvider(config.getProvider());
            clone.setName(name.trim());
            clone.setSamplePath(runtimePathConfig.resolveVoiceSampleDir().relativize(target).toString().replace('\\', '/'));
            clone.setSampleMimeType(mimeType);
            clone.setSampleSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sample)));
            clone.setStatus("CREATING");
            mapper.insertVoiceClone(clone);

            Thread.startVirtualThread(() -> submitClone(provider, config, clone, sample, sourceUrl,
                    requestedVoiceId, sampleText));
            return clone;
        } catch (Exception e) {
            throw new IllegalStateException("保存音色样本失败: " + e.getMessage(), e);
        }
    }

    public VoiceClone refresh(Integer userId, Long cloneId) {
        VoiceClone clone = requireClone(userId, cloneId);
        if (!"CREATING".equals(clone.getStatus())) {
            return clone;
        }
        try {
            ConfigBO config = requireConfig(userId, clone.getConfigId());
            applyResult(clone, requireProvider(clone.getProvider()).refresh(config, clone));
            return requireClone(userId, cloneId);
        } catch (Exception e) {
            throw new IllegalStateException("刷新音色状态失败: " + e.getMessage(), e);
        }
    }

    public List<VoiceClone> list(Integer userId) {
        return mapper.listVoiceClones(userId);
    }

    public PreviewAudio preview(Integer userId, Long cloneId, String text) {
        VoiceClone clone = requireClone(userId, cloneId);
        if (!"READY".equals(clone.getStatus())) {
            throw new IllegalStateException("音色尚未就绪");
        }
        if (!StringUtils.hasText(text) || text.length() > 500) {
            throw new IllegalArgumentException("试听文本长度必须在 1-500 字之间");
        }
        try {
            ConfigBO config = requireConfig(userId, clone.getConfigId());
            if ("mimo".equals(clone.getProvider())) {
                byte[] sample = Files.readAllBytes(resolveSample(clone));
                byte[] audio = requireProvider("mimo").synthesize(config, clone, sample, text);
                return new PreviewAudio(audio, "audio/wav", "wav");
            }
            Path audio = ttsServiceFactory.getTtsService(config, clone.getProviderVoiceId(), 1.0, 1.0)
                    .textToSpeech(text);
            String extension = fileExtension(audio.getFileName().toString());
            return new PreviewAudio(Files.readAllBytes(audio), mimeForExtension(extension), extension);
        } catch (Exception e) {
            throw new IllegalStateException("音色试听失败: " + e.getMessage(), e);
        }
    }

    public int delete(Integer userId, Long cloneId) {
        VoiceClone clone = requireClone(userId, cloneId);
        int rows = mapper.deleteVoiceClone(cloneId, userId);
        if (rows > 0) {
            try {
                Files.deleteIfExists(resolveSample(clone));
            } catch (Exception e) {
                log.warn("[音色克隆] 删除私有样本失败，cloneId={}", cloneId, e);
            }
        }
        return rows;
    }

    private void submitClone(VoiceCloneProvider provider, ConfigBO config, VoiceClone clone, byte[] sample,
                             String sourceUrl, String requestedVoiceId, String sampleText) {
        try {
            applyResult(clone, provider.create(config, clone, sample, sourceUrl, requestedVoiceId, sampleText));
        } catch (Exception e) {
            log.error("[音色克隆] 提交失败，provider={}, cloneId={}", provider.provider(), clone.getCloneId(), e);
            clone.setStatus("FAILED");
            clone.setErrorMessage(truncate(e.getMessage(), 500));
            mapper.updateVoiceClone(clone);
        }
    }

    private void applyResult(VoiceClone clone, VoiceCloneProvider.CloneResult result) {
        clone.setProviderTaskId(result.taskId());
        clone.setProviderVoiceId(result.voiceId());
        clone.setStatus(result.status());
        clone.setErrorMessage(truncate(result.errorMessage(), 500));
        mapper.updateVoiceClone(clone);
    }

    private ConfigBO requireConfig(Integer userId, Integer configId) {
        ConfigBO config = configService.getBO(configId);
        if (config == null || (!userId.equals(config.getUserId()) && !Integer.valueOf(1).equals(config.getUserId()))) {
            throw new IllegalArgumentException("音色克隆配置不存在或不属于当前用户");
        }
        return config;
    }

    private VoiceClone requireClone(Integer userId, Long cloneId) {
        VoiceClone clone = mapper.findVoiceClone(cloneId, userId);
        if (clone == null) throw new IllegalArgumentException("克隆音色不存在");
        return clone;
    }

    private VoiceCloneProvider requireProvider(String provider) {
        VoiceCloneProvider value = providers.get(provider == null ? "" : provider.toLowerCase(Locale.ROOT));
        if (value == null) throw new IllegalArgumentException("暂不支持音色克隆 Provider: " + provider);
        return value;
    }

    private Path resolveSample(VoiceClone clone) {
        Path path = runtimePathConfig.resolveVoiceSampleDir().resolve(clone.getSamplePath()).normalize();
        ensureWithin(runtimePathConfig.resolveVoiceSampleDir(), path);
        return path;
    }

    private void ensureWithin(Path root, Path target) {
        if (!target.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("非法样本路径");
        }
    }

    private String extension(String mime) {
        return mime.contains("wav") ? ".wav" : mime.contains("mp4") || mime.contains("m4a") ? ".m4a" : ".mp3";
    }

    private String fileExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "mp3" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeForExtension(String extension) {
        return "wav".equals(extension) ? "audio/wav" : "audio/mpeg";
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record PreviewAudio(byte[] bytes, String contentType, String extension) {
    }
}
