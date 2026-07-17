package com.xiaozhi.firmware.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Getter
public class FirmwareRelease {

    private static final Pattern BOARD_TYPE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,99}");

    private Long releaseId;
    private String boardType;
    private String version;
    private String filePath;
    private Long fileSize;
    private String sha256;
    private boolean forceUpdate;
    private boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private FirmwareRelease() {
    }

    public static FirmwareRelease create(String boardType, String version, String filePath,
                                         long fileSize, String sha256,
                                         boolean forceUpdate, boolean enabled) {
        validateBoardType(boardType);
        FirmwareVersion.parse(version);
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("固件文件路径不能为空");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("固件文件不能为空");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("固件 SHA-256 无效");
        }
        FirmwareRelease release = new FirmwareRelease();
        release.boardType = boardType;
        release.version = version;
        release.filePath = filePath;
        release.fileSize = fileSize;
        release.sha256 = sha256;
        release.forceUpdate = forceUpdate;
        release.enabled = enabled;
        return release;
    }

    public static FirmwareRelease reconstitute(Long releaseId, String boardType, String version,
                                                String filePath, Long fileSize, String sha256,
                                                boolean forceUpdate, boolean enabled,
                                                LocalDateTime createTime, LocalDateTime updateTime) {
        FirmwareRelease release = new FirmwareRelease();
        release.releaseId = releaseId;
        release.boardType = boardType;
        release.version = version;
        release.filePath = filePath;
        release.fileSize = fileSize;
        release.sha256 = sha256;
        release.forceUpdate = forceUpdate;
        release.enabled = enabled;
        release.createTime = createTime;
        release.updateTime = updateTime;
        return release;
    }

    public static void validateBoardType(String boardType) {
        if (boardType == null || !BOARD_TYPE.matcher(boardType).matches()) {
            throw new IllegalArgumentException("板型只能包含小写字母、数字、下划线和连字符，最长100字符");
        }
    }

    public void assignId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
