package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnifiedWatchNextTest {
    @Test public void acceptsEitherThresholdAtItsBoundary() {
        assertTrue(entry(30_000, 600_000).meetsMinimum(5, 5));
        assertTrue(entry(300_000, 7_200_000).meetsMinimum(5, 5));
        assertFalse(entry(29_999, 600_000).meetsMinimum(5, 5));
        assertFalse(entry(299_999, 7_200_000).meetsMinimum(5, 5));
    }

    @Test public void handlesUnavailableDurationAndDisabledThresholds() {
        assertTrue(entry(300_000, 0).meetsMinimum(5, 5));
        assertFalse(entry(300_000, 0).meetsMinimum(5, 0));
        assertFalse(entry(0, 600_000).meetsMinimum(5, 5));
        assertTrue(entry(0, 0).meetsMinimum(0, 0));
    }

    private UnifiedWatchNext.Entry entry(long position, long duration) {
        return new UnifiedWatchNext.Entry("example", "Example", "Title", "", "",
                0, position, duration, false, "");
    }
}
