package com.xiaozhi.firmware.domain;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * 与 xiaozhi-esp32 Ota::IsNewVersionAvailable 完全一致的数字点分版本。
 * 固件不支持 SemVer 的预发布和构建元数据语法。
 */
public final class FirmwareVersion implements Comparable<FirmwareVersion> {

    private static final Pattern FORMAT = Pattern.compile("(?:0|[1-9]\\d{0,4})(?:\\.(?:0|[1-9]\\d{0,4})){1,3}");

    private final String value;
    private final int[] segments;

    private FirmwareVersion(String value, int[] segments) {
        this.value = value;
        this.segments = segments;
    }

    public static FirmwareVersion parse(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("固件版本必须是2到4段数字点分格式，例如 1.6.0");
        }
        int[] segments = Arrays.stream(value.split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
        return new FirmwareVersion(value, segments);
    }

    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    @Override
    public int compareTo(FirmwareVersion other) {
        int commonLength = Math.min(segments.length, other.segments.length);
        for (int i = 0; i < commonLength; i++) {
            int compared = Integer.compare(segments[i], other.segments[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(segments.length, other.segments.length);
    }

    @Override
    public String toString() {
        return value;
    }
}
