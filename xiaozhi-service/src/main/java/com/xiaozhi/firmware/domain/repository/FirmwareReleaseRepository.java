package com.xiaozhi.firmware.domain.repository;

import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.firmware.domain.FirmwareRelease;

import java.util.Optional;

public interface FirmwareReleaseRepository {

    Optional<FirmwareRelease> findById(Long releaseId);

    Optional<FirmwareRelease> findByBoardTypeAndVersion(String boardType, String version);

    Optional<FirmwareRelease> findUpdate(String boardType, String currentVersion);

    PageResp<FirmwareRelease> page(int pageNo, int pageSize, String boardType, Boolean enabled);

    void save(FirmwareRelease release);

    void delete(Long releaseId);
}
