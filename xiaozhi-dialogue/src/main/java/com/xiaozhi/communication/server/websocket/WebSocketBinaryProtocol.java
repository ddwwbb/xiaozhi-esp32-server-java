package com.xiaozhi.communication.server.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * xiaozhi-esp32 WebSocket 二进制音频协议编解码器。
 */
public final class WebSocketBinaryProtocol {

    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;
    public static final int VERSION_3 = 3;

    private static final int TYPE_OPUS = 0;
    private static final int V2_HEADER_SIZE = 16;
    private static final int V3_HEADER_SIZE = 4;
    private static final int V3_MAX_PAYLOAD_SIZE = 0xFFFF;

    private WebSocketBinaryProtocol() {
    }

    public static boolean isSupported(int version) {
        return version >= VERSION_1 && version <= VERSION_3;
    }

    public static AudioFrame decode(int version, ByteBuffer source) {
        if (!isSupported(version)) {
            throw new IllegalArgumentException("不支持的WebSocket协议版本: " + version);
        }

        ByteBuffer buffer = source.slice().order(ByteOrder.BIG_ENDIAN);
        return switch (version) {
            case VERSION_1 -> new AudioFrame(readRemaining(buffer), 0);
            case VERSION_2 -> decodeV2(buffer);
            case VERSION_3 -> decodeV3(buffer);
            default -> throw new IllegalStateException("无法到达的协议版本: " + version);
        };
    }

    public static byte[] encode(int version, byte[] opusPayload) {
        return encode(version, opusPayload, 0);
    }

    public static byte[] encode(int version, byte[] opusPayload, long timestamp) {
        if (!isSupported(version)) {
            throw new IllegalArgumentException("不支持的WebSocket协议版本: " + version);
        }
        if (opusPayload == null) {
            throw new IllegalArgumentException("Opus负载不能为空");
        }

        return switch (version) {
            case VERSION_1 -> opusPayload.clone();
            case VERSION_2 -> ByteBuffer.allocate(V2_HEADER_SIZE + opusPayload.length)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putShort((short) VERSION_2)
                    .putShort((short) TYPE_OPUS)
                    .putInt(0)
                    .putInt((int) timestamp)
                    .putInt(opusPayload.length)
                    .put(opusPayload)
                    .array();
            case VERSION_3 -> encodeV3(opusPayload);
            default -> throw new IllegalStateException("无法到达的协议版本: " + version);
        };
    }

    private static AudioFrame decodeV2(ByteBuffer buffer) {
        requireHeader(buffer, V2_HEADER_SIZE, VERSION_2);
        int frameVersion = Short.toUnsignedInt(buffer.getShort());
        int type = Short.toUnsignedInt(buffer.getShort());
        buffer.getInt();
        long timestamp = Integer.toUnsignedLong(buffer.getInt());
        long payloadSize = Integer.toUnsignedLong(buffer.getInt());

        if (frameVersion != VERSION_2) {
            throw new IllegalArgumentException("WebSocket v2帧版本错误: " + frameVersion);
        }
        requireOpusType(type, VERSION_2);
        requireExactPayloadSize(buffer, payloadSize, VERSION_2);
        return new AudioFrame(readRemaining(buffer), timestamp);
    }

    private static AudioFrame decodeV3(ByteBuffer buffer) {
        requireHeader(buffer, V3_HEADER_SIZE, VERSION_3);
        int type = Byte.toUnsignedInt(buffer.get());
        buffer.get();
        int payloadSize = Short.toUnsignedInt(buffer.getShort());

        requireOpusType(type, VERSION_3);
        requireExactPayloadSize(buffer, payloadSize, VERSION_3);
        return new AudioFrame(readRemaining(buffer), 0);
    }

    private static byte[] encodeV3(byte[] opusPayload) {
        if (opusPayload.length > V3_MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("WebSocket v3负载超过65535字节: " + opusPayload.length);
        }
        return ByteBuffer.allocate(V3_HEADER_SIZE + opusPayload.length)
                .order(ByteOrder.BIG_ENDIAN)
                .put((byte) TYPE_OPUS)
                .put((byte) 0)
                .putShort((short) opusPayload.length)
                .put(opusPayload)
                .array();
    }

    private static void requireHeader(ByteBuffer buffer, int headerSize, int version) {
        if (buffer.remaining() < headerSize) {
            throw new IllegalArgumentException("WebSocket v" + version + "二进制帧头不完整");
        }
    }

    private static void requireOpusType(int type, int version) {
        if (type != TYPE_OPUS) {
            throw new IllegalArgumentException("WebSocket v" + version + "不支持的二进制类型: " + type);
        }
    }

    private static void requireExactPayloadSize(ByteBuffer buffer, long payloadSize, int version) {
        if (payloadSize != buffer.remaining()) {
            throw new IllegalArgumentException(
                    "WebSocket v" + version + "负载长度不匹配: 声明=" + payloadSize + ", 实际=" + buffer.remaining());
        }
    }

    private static byte[] readRemaining(ByteBuffer buffer) {
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return payload;
    }

    public record AudioFrame(byte[] payload, long timestamp) {
    }
}
