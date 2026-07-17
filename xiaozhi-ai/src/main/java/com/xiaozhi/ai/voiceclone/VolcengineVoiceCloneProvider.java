package com.xiaozhi.ai.voiceclone;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.ai.utils.HttpUtil;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;
import com.xiaozhi.utils.JsonUtil;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class VolcengineVoiceCloneProvider implements VoiceCloneProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String UPLOAD_URL = "https://openspeech.bytedance.com/api/v1/mega_tts/audio/upload";
    private static final String STATUS_URL = "https://openspeech.bytedance.com/api/v1/mega_tts/status";

    @Override
    public String provider() {
        return "volcengine";
    }

    @Override
    public CloneResult create(ConfigBO config, VoiceClone clone, byte[] sample, String sourceUrl,
                              String requestedVoiceId, String sampleText) throws Exception {
        require(config.getAppId(), "火山 AppID");
        require(config.getApiKey(), "火山 Access Token/API Key");
        require(requestedVoiceId, "火山控制台 SpeakerID");
        if (sample.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("火山参考音频不能超过 10MB");
        }
        String format = audioFormat(clone.getSampleMimeType());
        Map<String, Object> audio = sampleText == null || sampleText.isBlank()
                ? Map.of("audio_bytes", Base64.getEncoder().encodeToString(sample), "audio_format", format)
                : Map.of("audio_bytes", Base64.getEncoder().encodeToString(sample), "audio_format", format,
                    "text", sampleText);
        Map<String, Object> payload = Map.of(
                "appid", config.getAppId(),
                "speaker_id", requestedVoiceId,
                "audios", List.of(audio),
                "source", 2,
                "language", 0,
                "model_type", 4,
                "extra_params", "{\"enable_check_audio_quality\":true}"
        );
        JsonNode root = execute(config, UPLOAD_URL, payload);
        int code = root.path("BaseResp").path("StatusCode").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("火山音色训练提交失败: "
                    + root.path("BaseResp").path("StatusMessage").asText("code=" + code));
        }
        return CloneResult.creating(requestedVoiceId, requestedVoiceId);
    }

    @Override
    public CloneResult refresh(ConfigBO config, VoiceClone clone) throws Exception {
        JsonNode root = execute(config, STATUS_URL,
                Map.of("appid", config.getAppId(), "speaker_id", clone.getProviderVoiceId()));
        int code = root.path("BaseResp").path("StatusCode").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("火山音色状态查询失败: "
                    + root.path("BaseResp").path("StatusMessage").asText("code=" + code));
        }
        int status = root.path("status").asInt(0);
        return switch (status) {
            case 2, 4 -> CloneResult.ready(clone.getProviderTaskId(), clone.getProviderVoiceId());
            case 3 -> new CloneResult(clone.getProviderTaskId(), clone.getProviderVoiceId(), "FAILED", "火山训练失败");
            default -> CloneResult.creating(clone.getProviderTaskId(), clone.getProviderVoiceId());
        };
    }

    private JsonNode execute(ConfigBO config, String url, Map<String, Object> payload) throws Exception {
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer;" + config.getApiKey())
                .header("Resource-Id", "seed-icl-2.0")
                .post(RequestBody.create(JsonUtil.toJson(payload), JSON)).build();
        try (Response response = HttpUtil.client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("火山声音复刻请求失败 HTTP " + response.code());
            }
            return JsonUtil.OBJECT_MAPPER.readTree(body);
        }
    }

    private String audioFormat(String mime) {
        if ("audio/wav".equals(mime) || "audio/x-wav".equals(mime)) return "wav";
        if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime)) return "mp3";
        if ("audio/mp4".equals(mime) || "audio/m4a".equals(mime)) return "m4a";
        throw new IllegalArgumentException("火山参考音频仅支持 WAV/MP3/M4A");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 未配置");
    }
}
