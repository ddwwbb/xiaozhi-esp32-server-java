package com.xiaozhi.ai.voiceclone;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;
import com.xiaozhi.utils.JsonUtil;
import org.springframework.stereotype.Component;

@Component
public class AliyunVoiceCloneProvider implements VoiceCloneProvider {

    private static final String DOMAIN = "nls-slp.cn-shanghai.aliyuncs.com";
    private static final String VERSION = "2019-08-19";

    @Override
    public String provider() {
        return "aliyun";
    }

    @Override
    public CloneResult create(ConfigBO config, VoiceClone clone, byte[] sample, String sourceUrl,
                              String requestedVoiceId, String sampleText) throws Exception {
        require(config.getAk(), "阿里云 AccessKey ID");
        require(config.getSk(), "阿里云 AccessKey Secret");
        require(sourceUrl, "阿里 CosyVoice 要求公网可访问的样本 sourceUrl");
        String prefix = requestedVoiceId == null || requestedVoiceId.isBlank()
                ? "xz" + clone.getCloneId() : requestedVoiceId;
        prefix = prefix.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (prefix.isBlank()) prefix = "xz" + clone.getCloneId();
        prefix = prefix.substring(0, Math.min(prefix.length(), 10));
        CommonRequest request = request("CosyVoiceClone");
        request.putBodyParameter("Url", sourceUrl);
        request.putBodyParameter("VoicePrefix", prefix);
        JsonNode root = execute(config, request);
        if (root.path("Code").asInt(-1) != 20000000) {
            throw new IllegalStateException("阿里 CosyVoice 复刻提交失败: " + root.path("Message").asText("unknown"));
        }
        String voiceName = root.path("VoiceName").asText(null);
        return CloneResult.ready(prefix, voiceName);
    }

    @Override
    public CloneResult refresh(ConfigBO config, VoiceClone clone) throws Exception {
        CommonRequest request = request("ListCosyVoice");
        request.putBodyParameter("VoicePrefix", clone.getProviderTaskId());
        request.putBodyParameter("PageSize", "10");
        request.putBodyParameter("PageIndex", "1");
        JsonNode root = execute(config, request);
        for (JsonNode voice : root.path("Voices")) {
            if (clone.getProviderVoiceId().equals(voice.path("VoiceName").asText())) {
                String status = voice.path("Status").asText();
                return "SUCCESS".equals(status)
                        ? CloneResult.ready(clone.getProviderTaskId(), clone.getProviderVoiceId())
                        : CloneResult.creating(clone.getProviderTaskId(), clone.getProviderVoiceId());
            }
        }
        return CloneResult.creating(clone.getProviderTaskId(), clone.getProviderVoiceId());
    }

    private CommonRequest request(String action) {
        CommonRequest request = new CommonRequest();
        request.setDomain(DOMAIN);
        request.setVersion(VERSION);
        request.setAction(action);
        request.setMethod(MethodType.POST);
        request.setProtocol(ProtocolType.HTTPS);
        return request;
    }

    private JsonNode execute(ConfigBO config, CommonRequest request) throws Exception {
        DefaultProfile profile = DefaultProfile.getProfile("cn-shanghai", config.getAk(), config.getSk());
        IAcsClient client = new DefaultAcsClient(profile);
        CommonResponse response = client.getCommonResponse(request);
        if (response.getHttpStatus() != 200) {
            throw new IllegalStateException("阿里 CosyVoice 请求失败 HTTP " + response.getHttpStatus());
        }
        return JsonUtil.OBJECT_MAPPER.readTree(response.getData());
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 未配置");
    }
}
