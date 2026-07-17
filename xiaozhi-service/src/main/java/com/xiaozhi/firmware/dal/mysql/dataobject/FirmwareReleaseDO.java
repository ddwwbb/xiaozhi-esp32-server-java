package com.xiaozhi.firmware.dal.mysql.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xiaozhi.common.model.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("firmware_release")
public class FirmwareReleaseDO extends BaseDO {

    @TableId(value = "releaseId", type = IdType.AUTO)
    private Long releaseId;
    private String boardType;
    private String version;
    private String filePath;
    private Long fileSize;
    private String sha256;
    private Boolean forceUpdate;
    private Boolean enabled;
}
