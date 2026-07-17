package com.xiaozhi.communication.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSenderTest {

    private final MessageSender sender = new MessageSender(event -> { });
    private final RecordingSession session = new RecordingSession("session-1");

    @Test
    void sendsFirmwareSupportedRebootCommand() throws Exception {
        sender.sendSystemReboot(session);

        JsonNode json = JsonUtil.OBJECT_MAPPER.readTree(session.lastTextMessage);
        assertThat(json.path("type").asText()).isEqualTo("system");
        assertThat(json.path("command").asText()).isEqualTo("reboot");
        assertThat(json.path("session_id").asText()).isEqualTo("session-1");
    }

    @Test
    void sendsFirmwareSupportedAlertFields() throws Exception {
        sender.sendAlert(session, "升级完成", "设备即将重启", "happy");

        JsonNode json = JsonUtil.OBJECT_MAPPER.readTree(session.lastTextMessage);
        assertThat(json.path("type").asText()).isEqualTo("alert");
        assertThat(json.path("status").asText()).isEqualTo("升级完成");
        assertThat(json.path("message").asText()).isEqualTo("设备即将重启");
        assertThat(json.path("emotion").asText()).isEqualTo("happy");
    }

    private static final class RecordingSession extends ChatSession {

        private String lastTextMessage;
        private boolean open = true;

        private RecordingSession(String sessionId) {
            super(sessionId);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean isAudioChannelOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void sendTextMessage(String message) {
            lastTextMessage = message;
        }

        @Override
        public void sendBinaryMessage(byte[] message) {
        }
    }
}
