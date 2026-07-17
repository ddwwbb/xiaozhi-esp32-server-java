package com.xiaozhi.firmware.storage;

import com.xiaozhi.common.exception.OperationFailedException;
import com.xiaozhi.common.exception.ResourceNotFoundException;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import com.xiaozhi.firmware.domain.FirmwareVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 本地不可变固件存储。数据库只保存相对路径。 */
@Slf4j
@Component
public class LocalFirmwareStorage {

    private final Path root;
    private final long maxFileSize;

    public LocalFirmwareStorage(
            @Value("${xiaozhi.firmware.storage-path:data/firmware}") String storagePath,
            @Value("${xiaozhi.firmware.max-file-size:16MB}") DataSize maxFileSize) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize.toBytes();
    }

    public StoredFirmware store(MultipartFile file, String boardType, String version) {
        validate(file, boardType, version);
        String relativePath = boardType + "/" + version + ".bin";
        Path target = resolve(relativePath);
        Path temporary = null;
        try {
            Files.createDirectories(target.getParent());
            temporary = target.getParent().resolve("." + version + "." + UUID.randomUUID() + ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = file.getInputStream();
                 OutputStream output = new DigestOutputStream(
                         Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), digest)) {
                input.transferTo(output);
            }
            long actualSize = Files.size(temporary);
            if (actualSize != file.getSize()) {
                throw new IOException("固件上传大小与实际写入大小不一致");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("固件目录不支持原子移动，请将临时目录与固件目录放在同一文件系统", e);
            }
            return new StoredFirmware(relativePath, actualSize, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteTemporary(temporary);
            throw new OperationFailedException("保存固件失败", e);
        }
    }

    public Path requireFile(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("固件文件不存在");
        }
        return path;
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.error("删除固件文件失败, path={}", relativePath, e);
        }
    }

    private void validate(MultipartFile file, String boardType, String version) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("固件文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("固件文件超过限制，最大允许 " + DataSize.ofBytes(maxFileSize).toMegabytes() + "MB");
        }
        FirmwareRelease.validateBoardType(boardType);
        FirmwareVersion.parse(version);
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".bin")) {
            throw new IllegalArgumentException("固件文件必须使用 .bin 扩展名");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/octet-stream")
                && !contentType.equals("application/macbinary")) {
            throw new IllegalArgumentException("固件 MIME 类型必须是 application/octet-stream");
        }
    }

    private Path resolve(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("固件路径越界");
        }
        return resolved;
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            log.warn("清理固件临时文件失败, path={}", temporary, e);
        }
    }

    public record StoredFirmware(String relativePath, long fileSize, String sha256) {
    }
}
