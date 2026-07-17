package com.xiaozhi.firmware;

import com.xiaozhi.communication.ServerAddressProvider;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import com.xiaozhi.firmware.domain.repository.FirmwareReleaseRepository;
import com.xiaozhi.firmware.storage.LocalFirmwareStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmwareAppServiceTest {

    @Mock
    private FirmwareReleaseRepository repository;

    @Mock
    private ServerAddressProvider serverAddressProvider;

    @TempDir
    Path tempDir;

    @Test
    void returnsStableDownloadUrlOnlyWhenFileExists() throws Exception {
        Path boardDir = tempDir.resolve("atk-dnesp32s3-box2-wifi");
        Files.createDirectories(boardDir);
        Files.write(boardDir.resolve("1.6.1.bin"), new byte[]{1, 2, 3});
        FirmwareRelease release = release("atk-dnesp32s3-box2-wifi/1.6.1.bin");
        when(repository.findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0")).thenReturn(Optional.of(release));
        when(serverAddressProvider.getServerAddress()).thenReturn("http://127.0.0.1:8091");
        FirmwareAppService service = service();

        var result = service.findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0");

        assertThat(result.version()).isEqualTo("1.6.1");
        assertThat(result.url()).isEqualTo("http://127.0.0.1:8091/api/device/firmware/9/download");
    }

    @Test
    void doesNotOfferReleaseWhoseFileIsMissing() {
        when(repository.findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0"))
                .thenReturn(Optional.of(release("atk-dnesp32s3-box2-wifi/missing.bin")));

        assertThat(service().findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0")).isNull();
    }

    private FirmwareAppService service() {
        return new FirmwareAppService(repository,
                new LocalFirmwareStorage(tempDir.toString(), DataSize.ofMegabytes(1)),
                serverAddressProvider);
    }

    private FirmwareRelease release(String path) {
        return FirmwareRelease.reconstitute(
                9L, "atk-dnesp32s3-box2-wifi", "1.6.1", path, 3L,
                "a".repeat(64), false, true, null, null);
    }
}
