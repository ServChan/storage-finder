package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class IndexFreshnessTest {
    private static final long NOW = 10_000_000_000L;

    @Test
    void classifiesFreshAgingAndStaleBoundaries() {
        assertEquals(IndexFreshness.FRESH,
                IndexFreshness.classify(NOW - IndexFreshness.FRESH_MILLIS, NOW));
        assertEquals(IndexFreshness.AGING,
                IndexFreshness.classify(NOW - IndexFreshness.FRESH_MILLIS - 1, NOW));
        assertEquals(IndexFreshness.AGING,
                IndexFreshness.classify(NOW - IndexFreshness.AGING_MILLIS, NOW));
        assertEquals(IndexFreshness.STALE,
                IndexFreshness.classify(NOW - IndexFreshness.AGING_MILLIS - 1, NOW));
    }

    @Test
    void futureTimestampsAreTreatedAsFresh() {
        assertEquals(IndexFreshness.FRESH, IndexFreshness.classify(NOW + 1_000L, NOW));
    }
}
