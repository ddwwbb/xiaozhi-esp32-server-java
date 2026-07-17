package com.xiaozhi.common.model.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "固件发布信息")
public record FirmwareReleaseResp(
        Long releaseId,
        String boardType,
        String version,
        Long fileSize,
        String sha256,
        Boolean forceUpdate,
        Boolean enabled,
        String downloadUrl,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
