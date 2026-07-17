package com.xiaozhi.firmware;

import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.common.model.req.FirmwareReleasePageReq;
import com.xiaozhi.common.model.resp.FirmwareReleaseResp;
import com.xiaozhi.common.model.resp.OtaResponse;
import com.xiaozhi.common.model.resp.PageResp;
import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import com.xiaozhi.firmware.domain.repository.FirmwareReleaseRepository;
import com.xiaozhi.firmware.storage.LocalFirmwareStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
@Service
public class FirmwareAppService {

    private final FirmwareReleaseRepository repository;
    private final LocalFirmwareStorage storage;
    private final ServerAddressProvider serverAddressProvider;

    public FirmwareAppService(FirmwareReleaseRepository repository,
                              LocalFirmwareStorage storage,
                              ServerAddressProvider serverAddressProvider) {
        this.repository = repository;
        this.storage = storage;
        this.serverAddressProvider = serverAddressProvider;
    }

    public PageResp<FirmwareReleaseResp> page(FirmwareReleasePageReq req) {
        PageResp<FirmwareRelease> page = repository.page(
                req.getPageNo(), req.getPageSize(), req.getBoardType(), req.getEnabled());
        return new PageResp<>(page.getList().stream().map(this::toResponse).toList(),
                page.getTotal(), page.getPageNo(), page.getPageSize());
    }

    @Transactional
    public FirmwareReleaseResp publish(MultipartFile file, String boardType, String version,
                                       boolean forceUpdate, boolean enabled) {
        if (repository.findByBoardTypeAndVersion(boardType, version).isPresent()) {
            throw new IllegalStateException("该板型和版本已存在");
        }
        LocalFirmwareStorage.StoredFirmware stored = storage.store(file, boardType, version);
        deleteFileOnRollback(stored.relativePath());
        FirmwareRelease release = FirmwareRelease.create(
                boardType, version, stored.relativePath(), stored.fileSize(), stored.sha256(),
                forceUpdate, enabled);
        repository.save(release);
        return toResponse(release);
    }

    @Transactional
    public FirmwareReleaseResp changeEnabled(Long releaseId, boolean enabled) {
        FirmwareRelease release = requireRelease(releaseId);
        if (enabled) {
            storage.requireFile(release.getFilePath());
        }
        release.changeEnabled(enabled);
        repository.save(release);
        return toResponse(release);
    }

    @Transactional
    public void delete(Long releaseId) {
        FirmwareRelease release = requireRelease(releaseId);
        if (release.isEnabled()) {
            throw new IllegalStateException("请先禁用固件再删除");
        }
        repository.delete(releaseId);
        deleteFileAfterCommit(release.getFilePath());
    }

    public OtaResponse.Firmware findUpdate(String boardType, String currentVersion) {
        try {
            return repository.findUpdate(boardType, currentVersion)
                    .filter(release -> {
                        try {
                            storage.requireFile(release.getFilePath());
                            return true;
                        } catch (ResourceNotFoundException e) {
                            log.error("已启用固件文件丢失，停止下发, releaseId={}, path={}",
                                    release.getReleaseId(), release.getFilePath());
                            return false;
                        }
                    })
                    .map(release -> new OtaResponse.Firmware(
                            release.getVersion(), downloadUrl(release.getReleaseId()), release.isForceUpdate() ? 1 : 0))
                    .orElse(null);
        } catch (RuntimeException e) {
            log.error("查询OTA固件失败，不阻断设备激活和对话地址下发, boardType={}, currentVersion={}",
                    boardType, currentVersion, e);
            return null;
        }
    }

    public FirmwareDownload requireDownload(Long releaseId) {
        FirmwareRelease release = requireRelease(releaseId);
        if (!release.isEnabled()) {
            throw new ResourceNotFoundException("固件不存在或已禁用");
        }
        Path path = storage.requireFile(release.getFilePath());
        return new FirmwareDownload(path, release.getFileSize(),
                release.getBoardType() + "-" + release.getVersion() + ".bin");
    }

    private FirmwareRelease requireRelease(Long releaseId) {
        return repository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("固件发布不存在"));
    }

    private FirmwareReleaseResp toResponse(FirmwareRelease release) {
        return new FirmwareReleaseResp(
                release.getReleaseId(), release.getBoardType(), release.getVersion(),
                release.getFileSize(), release.getSha256(), release.isForceUpdate(), release.isEnabled(),
                release.getReleaseId() == null ? null : downloadUrl(release.getReleaseId()),
                release.getCreateTime(), release.getUpdateTime());
    }

    private String downloadUrl(Long releaseId) {
        return serverAddressProvider.getServerAddress() + "/api/device/firmware/" + releaseId + "/download";
    }

    private void deleteFileOnRollback(String relativePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(relativePath);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String relativePath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(relativePath);
            }
        });
    }

    public record FirmwareDownload(Path path, long fileSize, String fileName) {
    }
}
