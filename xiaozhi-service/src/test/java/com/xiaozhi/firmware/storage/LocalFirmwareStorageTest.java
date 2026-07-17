package com.xiaozhi.firmware.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFirmwareStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesImmutableFirmwareAndCalculatesSha256() throws Exception {
        LocalFirmwareStorage storage = new LocalFirmwareStorage(tempDir.toString(), DataSize.ofMegabytes(1));
        byte[] content = new byte[]{1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile(
                "file", "firmware.bin", "application/octet-stream", content);

        LocalFirmwareStorage.StoredFirmware stored = storage.store(
                file, "atk-dnesp32s3-box2-wifi", "1.6.0");

        assertThat(stored.relativePath()).isEqualTo("atk-dnesp32s3-box2-wifi/1.6.0.bin");
        assertThat(stored.fileSize()).isEqualTo(content.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(Files.readAllBytes(storage.requireFile(stored.relativePath()))).isEqualTo(content);
    }

    @Test
    void rejectsTraversalAndUnsupportedFile() {
        LocalFirmwareStorage storage = new LocalFirmwareStorage(tempDir.toString(), DataSize.ofMegabytes(1));
        MockMultipartFile file = new MockMultipartFile(
                "file", "firmware.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> storage.store(file, "../escape", "1.6.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(file, "atk-dnesp32s3-box2-wifi", "1.6.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".bin");
    }
}
