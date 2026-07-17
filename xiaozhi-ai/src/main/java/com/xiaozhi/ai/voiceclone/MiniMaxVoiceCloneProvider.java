package com.xiaozhi.ai.voiceclone;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.ai.utils.HttpUtil;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;
import com.xiaozhi.utils.JsonUtil;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MiniMaxVoiceCloneProvider implements VoiceCloneProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Override
    public String provider() {
        return "minimax";
    }

    @Override
    public CloneResult create(ConfigBO config, VoiceClone clone, byte[] sample, String sourceUrl,
                              String requestedVoiceId, String sampleText) throws Exception {
        require(config.getApiKey(), "MiniMax API Key");
        require(config.getAppId(), "MiniMax Group ID");
        String voiceId = requestedVoiceId;
        if (voiceId == null || !voiceId.matches("[A-Za-z][A-Za-z0-9_-]{6,254}[A-Za-z0-9]")) {
            voiceId = "Xiaozhi" + clone.getCloneId();
        }

        RequestBody fileBody = RequestBody.create(sample, MediaType.parse(clone.getSampleMimeType()));
        MultipartBody uploadBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("purpose", "voice_clone")
                .addFormDataPart("file", "voice-sample" + extension(clone.getSampleMimeType()), fileBody)
                .build();
        Request upload = new Request.Builder()
                .url("https://api.minimaxi.com/v1/files/upload?GroupId=" + config.getAppId())
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(uploadBody).build();
        long fileId;
        try (Response response = HttpUtil.client.newCall(upload).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("MiniMax 样本上传失败 HTTP " + response.code());
            }
            JsonNode root = JsonUtil.OBJECT_MAPPER.readTree(body);
            fileId = root.path("file").path("file_id").asLong(0);
            if (fileId <= 0) {
                throw new IllegalStateException("MiniMax 样本上传响应缺少 file_id");
            }
        }

        Request cloneRequest = new Request.Builder()
                .url("https://api.minimaxi.com/v1/voice_clone?GroupId=" + config.getAppId())
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(JsonUtil.toJson(Map.of("file_id", fileId, "voice_id", voiceId,
                        "need_noise_reduction", true, "need_volume_normalization", true)), JSON))
                .build();
        try (Response response = HttpUtil.client.newCall(cloneRequest).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("MiniMax 音色复刻失败 HTTP " + response.code());
            }
            JsonNode root = JsonUtil.OBJECT_MAPPER.readTree(body);
            int statusCode = root.path("base_resp").path("status_code").asInt(-1);
            if (statusCode != 0) {
                throw new IllegalStateException("MiniMax 音色复刻失败: "
                        + root.path("base_resp").path("status_msg").asText("unknown"));
            }
        }
        return CloneResult.ready(String.valueOf(fileId), voiceId);
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 未配置");
        }
    }

    private String extension(String mimeType) {
        if ("audio/wav".equals(mimeType) || "audio/x-wav".equals(mimeType)) {
            return ".wav";
        }
        if ("audio/mp4".equals(mimeType) || "audio/m4a".equals(mimeType)) {
            return ".m4a";
        }
        return ".mp3";
    }
}
