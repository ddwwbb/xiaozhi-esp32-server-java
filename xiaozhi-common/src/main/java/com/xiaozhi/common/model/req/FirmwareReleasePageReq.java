package com.xiaozhi.common.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "固件发布分页查询")
public class FirmwareReleasePageReq extends BasePageReq {

    @Schema(description = "固件板型")
    private String boardType;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
