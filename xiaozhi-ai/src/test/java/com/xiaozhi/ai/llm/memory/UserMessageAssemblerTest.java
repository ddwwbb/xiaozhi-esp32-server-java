package com.xiaozhi.ai.llm.memory;

import com.xiaozhi.common.model.bo.MessageMetadataBO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserMessageAssemblerTest {

    @Test
    void addsSpeakerBeforeEmotionWithoutChangingMetadata() {
        MessageMetadataBO metadata = MessageMetadataBO.builder()
                .speakerName("张三")
                .emotion("happy")
                .build();
        UserMessage source = UserMessage.builder()
                .text("今天吃什么？")
                .metadata(Map.of(MessageMetadataBO.METADATA_KEY, metadata))
                .build();

        UserMessage assembled = (UserMessage) UserMessageAssembler.assemble(source);

        assertThat(assembled.getText()).isEqualTo("[说话人:张三][happy] 今天吃什么？");
        assertThat(assembled.getMetadata()).containsEntry(MessageMetadataBO.METADATA_KEY, metadata);
    }

    @Test
    void keepsLegacyThreeArgumentOverload() {
        assertThat(UserMessageAssembler.assemble("你好", null, "neutral"))
                .isEqualTo("[neutral] 你好");
    }
}
