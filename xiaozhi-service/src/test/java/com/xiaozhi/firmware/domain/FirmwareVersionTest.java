package com.xiaozhi.firmware.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirmwareVersionTest {

    @Test
    void comparesVersionsLikeEsp32Firmware() {
        assertThat(FirmwareVersion.parse("1.6.1")).isGreaterThan(FirmwareVersion.parse("1.6.0"));
        assertThat(FirmwareVersion.parse("2.0")).isGreaterThan(FirmwareVersion.parse("1.99.99"));
        assertThat(FirmwareVersion.parse("1.6.0")).isGreaterThan(FirmwareVersion.parse("1.6"));
        assertThat(FirmwareVersion.parse("1.6.0")).isEqualByComparingTo(FirmwareVersion.parse("1.6.0"));
    }

    @Test
    void rejectsVersionSyntaxUnsupportedByFirmware() {
        assertThatThrownBy(() -> FirmwareVersion.parse("v1.6.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirmwareVersion.parse("1.6.0-rc.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirmwareVersion.parse("1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
