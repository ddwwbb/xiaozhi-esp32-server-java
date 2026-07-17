package com.xiaozhi.ai.voiceprint;

import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import com.xiaozhi.common.config.RuntimePathConfig;
import com.xiaozhi.personal.model.PersonalModels.VoiceProfile;
import com.xiaozhi.personal.service.VoiceProfileService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 sherpa-onnx SpeakerEmbeddingExtractor 的本地声纹服务。
 * 模型未配置时能力显式关闭，不使用伪向量或静默降级。
 */
@Service
@RequiredArgsConstructor
public class SpeakerRecognitionService {

    private static final int SAMPLE_RATE = 16_000;
    private final RuntimePathConfig runtimePathConfig;
    private final VoiceProfileService voiceProfileService;
    private final Object extractorLock = new Object();
    private volatile SpeakerEmbeddingExtractor extractor;

    public boolean isEnabled() {
        Path model = runtimePathConfig.resolveSpeakerEmbeddingModel();
        return model != null && Files.isRegularFile(model);
    }

    public VoiceProfile enroll(Integer userId, String displayName, List<byte[]> pcmSamples,
                               BigDecimal threshold) {
        if (pcmSamples == null || pcmSamples.size() < 3) {
            throw new IllegalArgumentException("声纹注册至少需要 3 段 WAV/PCM 样本");
        }
        List<List<Double>> embeddings = new ArrayList<>(pcmSamples.size());
        for (byte[] sample : pcmSamples) {
            embeddings.add(extract(sample));
        }
        Path model = requireModel();
        return voiceProfileService.enroll(userId, displayName, model.getFileName().toString(),
                modelVersion(model), embeddings, threshold);
    }

    public VoiceProfileService.MatchResult identify(Integer userId, byte[] pcm16le) {
        if (!isEnabled() || pcm16le == null || pcm16le.length < SAMPLE_RATE * 2) {
            return VoiceProfileService.MatchResult.unknownResult();
        }
        return voiceProfileService.identify(userId, extract(pcm16le));
    }

    public List<Double> extract(byte[] wavOrPcm) {
        byte[] pcm = stripWavHeader(wavOrPcm);
        if (pcm.length < SAMPLE_RATE * 2) {
            throw new IllegalArgumentException("声纹样本有效语音不得少于 1 秒");
        }
        float[] samples = pcm16ToFloat(pcm);
        synchronized (extractorLock) {
            SpeakerEmbeddingExtractor current = getOrCreateExtractor();
            OnlineStream stream = current.createStream();
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE);
                stream.inputFinished();
                if (!current.isReady(stream)) {
                    throw new IllegalArgumentException("声纹样本过短或无有效语音");
                }
                float[] vector = current.compute(stream);
                if (vector == null || vector.length == 0) {
                    throw new IllegalStateException("声纹模型未返回嵌入向量");
                }
                List<Double> result = new ArrayList<>(vector.length);
                for (float value : vector) result.add((double) value);
                return result;
            } finally {
                stream.release();
            }
        }
    }

    private SpeakerEmbeddingExtractor getOrCreateExtractor() {
        if (extractor == null) {
            Path model = requireModel();
            SpeakerEmbeddingExtractorConfig config = SpeakerEmbeddingExtractorConfig.builder()
                    .setModel(model.toString())
                    .setNumThreads(2)
                    .setDebug(false)
                    .setProvider("cpu")
                    .build();
            extractor = new SpeakerEmbeddingExtractor(config);
        }
        return extractor;
    }

    private Path requireModel() {
        Path model = runtimePathConfig.resolveSpeakerEmbeddingModel();
        if (model == null || !Files.isRegularFile(model)) {
            throw new IllegalStateException("未配置有效的 xiaozhi.runtime.speaker-embedding-model");
        }
        return model;
    }

    private byte[] stripWavHeader(byte[] data) {
        if (data == null || data.length == 0) return new byte[0];
        if (data.length > 44 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            int offset = 12;
            while (offset + 8 <= data.length) {
                int size = (data[offset + 4] & 0xff) | ((data[offset + 5] & 0xff) << 8)
                        | ((data[offset + 6] & 0xff) << 16) | ((data[offset + 7] & 0xff) << 24);
                if (data[offset] == 'd' && data[offset + 1] == 'a'
                        && data[offset + 2] == 't' && data[offset + 3] == 'a') {
                    int start = offset + 8;
                    int end = Math.min(start + Math.max(size, 0), data.length);
                    return java.util.Arrays.copyOfRange(data, start, end);
                }
                offset += 8 + Math.max(size, 0) + (size & 1);
            }
            throw new IllegalArgumentException("WAV 文件缺少 data 块");
        }
        return data;
    }

    private float[] pcm16ToFloat(byte[] pcm) {
        float[] result = new float[pcm.length / 2];
        for (int i = 0; i < result.length; i++) {
            int low = pcm[i * 2] & 0xff;
            int high = pcm[i * 2 + 1];
            result[i] = (short) ((high << 8) | low) / 32768.0f;
        }
        return result;
    }

    private String modelVersion(Path model) {
        try {
            return Files.size(model) + "-" + Files.getLastModifiedTime(model).toMillis();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @PreDestroy
    public void close() {
        synchronized (extractorLock) {
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }
}
