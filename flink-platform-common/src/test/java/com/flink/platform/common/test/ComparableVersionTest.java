package com.flink.platform.common.test;

import com.flink.platform.common.util.ComparableVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ComparableVersion.
 */
class ComparableVersionTest {

    @Test
    void testBasicComparison() {
        assertTrue(v("1.12.0").compareTo(v("1.15.1")) < 0);
        assertTrue(v("1.15.1").compareTo(v("1.12.0")) > 0);
        assertTrue(v("1.17.1").compareTo(v("1.15.1")) > 0);
    }

    @Test
    void testMissingSegmentsTreatedAsZero() {
        assertEquals(v("1"), v("1"));
        assertEquals(v("1"), v("1.0"));
        assertEquals(v("1"), v("1.0.0"));
        assertEquals(v("1.0"), v("1.0.0"));
    }

    @Test
    void testHashCodeConsistentWithEquals() {
        assertEquals(v("1.12").hashCode(), v("1.12.0").hashCode());
        assertEquals(v("1.0.0").hashCode(), v("1").hashCode());
    }

    @Test
    void testMajorVersionDifference() {
        assertTrue(v("2.0.0").compareTo(v("1.99.99")) > 0);
    }

    private static ComparableVersion v(String version) {
        return new ComparableVersion(version);
    }
}
