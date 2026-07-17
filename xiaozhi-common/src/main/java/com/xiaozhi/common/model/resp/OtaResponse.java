package com.xiaozhi.common.model.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * xiaozhi-esp32 OTA 检查接口的根响应，禁止使用管理端 ApiResponse 包装。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtaResponse(
        Activation activation,
        Websocket websocket,
        @JsonProperty("server_time") ServerTime serverTime,
        Firmware firmware) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Activation(String code, String message, String challenge,
                             @JsonProperty("timeout_ms") Integer timeoutMs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Websocket(String url, String token, int version) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ServerTime(long timestamp, @JsonProperty("timezone_offset") int timezoneOffset) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Firmware(String version, String url, Integer force) {
    }
}
