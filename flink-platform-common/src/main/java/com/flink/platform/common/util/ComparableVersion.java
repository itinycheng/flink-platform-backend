package com.flink.platform.common.util;

import lombok.Getter;
import lombok.ToString;

/**
 * <pre>
 * Numeric-only version comparator.
 *
 * Supports versions like "1.12.0", "1.15.1", "2.0".
 * Missing segments are treated as 0 (e.g. "1.12" == "1.12.0").
 * </pre>
 */
@Getter
@ToString(exclude = "segments")
public final class ComparableVersion implements Comparable<ComparableVersion> {

    private final String version;
    private final int[] segments;

    public ComparableVersion(String version) {
        this.version = version.trim();
        String[] parts = this.version.split("\\.");
        this.segments = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            this.segments[i] = Integer.parseInt(parts[i]);
        }
    }

    @Override
    public int compareTo(ComparableVersion other) {
        int len = Math.max(this.segments.length, other.segments.length);
        for (int i = 0; i < len; i++) {
            int v1 = i < this.segments.length ? this.segments[i] : 0;
            int v2 = i < other.segments.length ? other.segments[i] : 0;
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComparableVersion)) {
            return false;
        }
        return compareTo((ComparableVersion) o) == 0;
    }

    @Override
    public int hashCode() {
        int end = segments.length;
        while (end > 0 && segments[end - 1] == 0) {
            end--;
        }

        int result = 0;
        for (int i = 0; i < end; i++) {
            result = 31 * result + segments[i];
        }
        return result;
    }
}
