package com.xiaozhi.firmware.infrastructure;

import com.xiaozhi.firmware.dal.mysql.dataobject.FirmwareReleaseDO;
import com.xiaozhi.firmware.dal.mysql.mapper.FirmwareReleaseMapper;
import com.xiaozhi.firmware.domain.FirmwareRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirmwareReleaseRepositoryImplTest {

    @Mock
    private FirmwareReleaseMapper mapper;

    @Test
    void selectsHighestCompatibleVersionInsteadOfNewestRow() {
        when(mapper.selectList(any())).thenReturn(List.of(
                release(1L, "1.7.0"),
                release(2L, "1.8.0"),
                release(3L, "1.6.9")));
        FirmwareReleaseRepositoryImpl repository = new FirmwareReleaseRepositoryImpl(
                mapper, new FirmwareReleaseConverter());

        FirmwareRelease selected = repository.findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0").orElseThrow();

        assertThat(selected.getReleaseId()).isEqualTo(2L);
        assertThat(selected.getVersion()).isEqualTo("1.8.0");
    }

    @Test
    void returnsEmptyForInvalidOrCurrentVersion() {
        FirmwareReleaseRepositoryImpl repository = new FirmwareReleaseRepositoryImpl(
                mapper, new FirmwareReleaseConverter());
        assertThat(repository.findUpdate("atk-dnesp32s3-box2-wifi", "v1.6.0")).isEmpty();

        when(mapper.selectList(any())).thenReturn(List.of(release(1L, "1.6.0")));
        assertThat(repository.findUpdate("atk-dnesp32s3-box2-wifi", "1.6.0")).isEmpty();
    }

    private FirmwareReleaseDO release(Long id, String version) {
        FirmwareReleaseDO release = new FirmwareReleaseDO();
        release.setReleaseId(id);
        release.setBoardType("atk-dnesp32s3-box2-wifi");
        release.setVersion(version);
        release.setFilePath("atk-dnesp32s3-box2-wifi/" + version + ".bin");
        release.setFileSize(4L);
        release.setSha256("a".repeat(64));
        release.setForceUpdate(false);
        release.setEnabled(true);
        return release;
    }
}
