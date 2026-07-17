package com.xiaozhi.firmware.infrastructure;

import com.xiaozhi.firmware.dal.mysql.dataobject.FirmwareReleaseDO;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import org.springframework.stereotype.Component;

@Component
public class FirmwareReleaseConverter {

    public FirmwareRelease toDomain(FirmwareReleaseDO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return FirmwareRelease.reconstitute(
                dataObject.getReleaseId(), dataObject.getBoardType(), dataObject.getVersion(),
                dataObject.getFilePath(), dataObject.getFileSize(), dataObject.getSha256(),
                Boolean.TRUE.equals(dataObject.getForceUpdate()), Boolean.TRUE.equals(dataObject.getEnabled()),
                dataObject.getCreateTime(), dataObject.getUpdateTime());
    }

    public FirmwareReleaseDO toDataObject(FirmwareRelease release) {
        FirmwareReleaseDO dataObject = new FirmwareReleaseDO();
        dataObject.setReleaseId(release.getReleaseId());
        dataObject.setBoardType(release.getBoardType());
        dataObject.setVersion(release.getVersion());
        dataObject.setFilePath(release.getFilePath());
        dataObject.setFileSize(release.getFileSize());
        dataObject.setSha256(release.getSha256());
        dataObject.setForceUpdate(release.isForceUpdate());
        dataObject.setEnabled(release.isEnabled());
        dataObject.setCreateTime(release.getCreateTime());
        dataObject.setUpdateTime(release.getUpdateTime());
        return dataObject;
    }
}
