package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "固件发布状态更新")
public record FirmwareReleaseStatusReq(
        @NotNull(message = "启用状态不能为空")
        @Schema(description = "是否允许 OTA 下发和下载")
        Boolean enabled) {
}
