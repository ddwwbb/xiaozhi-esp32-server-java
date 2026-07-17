package com.xiaozhi.communication.server.websocket;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketBinaryProtocolTest {

    private static final byte[] OPUS_PAYLOAD = {0x01, 0x02, (byte) 0xFF};

    @Test
    void version1UsesRawOpusPayloadAndRespectsBufferPosition() {
        ByteBuffer source = ByteBuffer.wrap(new byte[]{0x55, 0x01, 0x02, (byte) 0xFF});
        source.position(1);

        var decoded = WebSocketBinaryProtocol.decode(1, source);

        assertThat(decoded.payload()).containsExactly(OPUS_PAYLOAD);
        assertThat(WebSocketBinaryProtocol.encode(1, OPUS_PAYLOAD)).containsExactly(OPUS_PAYLOAD);
    }

    @Test
    void version2RoundTripsOpusPayloadAndTimestamp() {
        long timestamp = 0xFEDCBA98L;

        byte[] encoded = WebSocketBinaryProtocol.encode(2, OPUS_PAYLOAD, timestamp);
        var decoded = WebSocketBinaryProtocol.decode(2, ByteBuffer.wrap(encoded));

        assertThat(encoded).hasSize(16 + OPUS_PAYLOAD.length);
        assertThat(decoded.payload()).containsExactly(OPUS_PAYLOAD);
        assertThat(decoded.timestamp()).isEqualTo(timestamp);
    }

    @Test
    void version3RoundTripsOpusPayload() {
        byte[] encoded = WebSocketBinaryProtocol.encode(3, OPUS_PAYLOAD);
        var decoded = WebSocketBinaryProtocol.decode(3, ByteBuffer.wrap(encoded));

        assertThat(encoded).hasSize(4 + OPUS_PAYLOAD.length);
        assertThat(decoded.payload()).containsExactly(OPUS_PAYLOAD);
        assertThat(decoded.timestamp()).isZero();
    }

    @Test
    void rejectsFrameWhoseDeclaredPayloadSizeDoesNotMatchActualSize() {
        byte[] encoded = WebSocketBinaryProtocol.encode(3, OPUS_PAYLOAD);
        encoded[3] = 0x04;

        assertThatThrownBy(() -> WebSocketBinaryProtocol.decode(3, ByteBuffer.wrap(encoded)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("负载长度不匹配");
    }

    @Test
    void rejectsUnsupportedProtocolVersion() {
        assertThatThrownBy(() -> WebSocketBinaryProtocol.decode(4, ByteBuffer.wrap(OPUS_PAYLOAD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的WebSocket协议版本");
    }
}
