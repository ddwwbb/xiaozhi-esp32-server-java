package com.xiaozhi.ai.voiceclone;

import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.personal.model.PersonalModels.VoiceClone;

public interface VoiceCloneProvider {

    String provider();

    CloneResult create(ConfigBO config, VoiceClone clone, byte[] sample, String sourceUrl,
                       String requestedVoiceId, String sampleText) throws Exception;

    default CloneResult refresh(ConfigBO config, VoiceClone clone) throws Exception {
        return new CloneResult(clone.getProviderTaskId(), clone.getProviderVoiceId(), clone.getStatus(),
                clone.getErrorMessage());
    }

    default byte[] synthesize(ConfigBO config, VoiceClone clone, byte[] sample, String text) throws Exception {
        throw new UnsupportedOperationException(provider() + " 不支持直接参考音频合成");
    }

    record CloneResult(String taskId, String voiceId, String status, String errorMessage) {
        public static CloneResult ready(String taskId, String voiceId) {
            return new CloneResult(taskId, voiceId, "READY", null);
        }

        public static CloneResult creating(String taskId, String voiceId) {
            return new CloneResult(taskId, voiceId, "CREATING", null);
        }
    }
}
