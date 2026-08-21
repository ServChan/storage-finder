package org.lts.storagefinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class IndexRetentionPolicyTest {
    @Test
    void removesOldestEntriesPerScopeBeforeApplyingGlobalLimit() {
        List<IndexRetentionPolicy.Entry> entries = List.of(
                new IndexRetentionPolicy.Entry("a-old", "a", 1),
                new IndexRetentionPolicy.Entry("a-new", "a", 5),
                new IndexRetentionPolicy.Entry("b-old", "b", 2),
                new IndexRetentionPolicy.Entry("b-mid", "b", 3),
                new IndexRetentionPolicy.Entry("b-new", "b", 4));

        assertEquals(Set.of("a-old", "b-old", "b-mid"),
                IndexRetentionPolicy.selectRemovals(entries, 2, 2));
    }

    @Test
    void usesKeyAsStableTieBreaker() {
        List<IndexRetentionPolicy.Entry> entries = List.of(
                new IndexRetentionPolicy.Entry("z", "scope", 1),
                new IndexRetentionPolicy.Entry("a", "scope", 1));

        assertEquals(Set.of("a"), IndexRetentionPolicy.selectRemovals(entries, 1, 10));
    }

    @Test
    void keepsEveryEntryWhenBothLimitsAreRespected() {
        List<IndexRetentionPolicy.Entry> entries = List.of(
                new IndexRetentionPolicy.Entry("a", "first", 1),
                new IndexRetentionPolicy.Entry("b", "second", 2));

        assertEquals(Set.of(), IndexRetentionPolicy.selectRemovals(entries, 2, 2));
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> IndexRetentionPolicy.selectRemovals(List.of(), 0, 1));
    }
}
