package com.xiaozhi.ai.voiceclone;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.ai.utils.HttpUtil;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;
import com.xiaozhi.utils.JsonUtil;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class MimoVoiceCloneProvider implements VoiceCloneProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    public String provider() {
        return "mimo";
    }

    @Override
    public CloneResult create(ConfigBO config, VoiceClone clone, byte[] sample, String sourceUrl,
                              String requestedVoiceId, String sampleText) {
        if (sample.length > 7_500_000) {
            throw new IllegalArgumentException("MiMo Base64 编码后不能超过 10MB，请缩短样本");
        }
        return CloneResult.ready("reference-audio", "mimo-clone-" + clone.getCloneId());
    }

    @Override
    public byte[] synthesize(ConfigBO config, VoiceClone clone, byte[] sample, String text) throws Exception {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalArgumentException("MiMo API Key 未配置");
        }
        String mime = normalizeMime(clone.getSampleMimeType());
        String voice = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(sample);
        Map<String, Object> payload = Map.of(
                "model", "mimo-v2.5-tts-voiceclone",
                "messages", List.of(
                        Map.of("role", "user", "content", ""),
                        Map.of("role", "assistant", "content", text)
                ),
                "audio", Map.of("format", "wav", "voice", voice)
        );
        String endpoint = config.getApiUrl() == null || config.getApiUrl().isBlank()
                ? "https://api.xiaomimimo.com/v1/chat/completions" : config.getApiUrl();
        Request request = new Request.Builder().url(endpoint)
                .header("api-key", config.getApiKey())
                .post(RequestBody.create(JsonUtil.toJson(payload), JSON)).build();
        try (Response response = HttpUtil.client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("MiMo 音色克隆合成失败 HTTP " + response.code());
            }
            JsonNode root = JsonUtil.OBJECT_MAPPER.readTree(body);
            String audio = root.path("choices").path(0).path("message").path("audio").path("data").asText(null);
            if (audio == null) {
                throw new IllegalStateException("MiMo 响应缺少 choices[0].message.audio.data");
            }
            return Base64.getDecoder().decode(audio);
        }
    }

    private String normalizeMime(String mime) {
        if ("audio/wav".equals(mime) || "audio/x-wav".equals(mime)) {
            return "audio/wav";
        }
        if ("audio/mpeg".equals(mime) || "audio/mp3".equals(mime)) {
            return "audio/mpeg";
        }
        throw new IllegalArgumentException("MiMo 仅支持 WAV/MP3 参考音频");
    }
}
