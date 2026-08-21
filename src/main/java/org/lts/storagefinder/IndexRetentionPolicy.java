package org.lts.storagefinder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure retention logic kept independent from Minecraft so it can be unit tested. */
public final class IndexRetentionPolicy {
    private IndexRetentionPolicy() {
    }

    public static Set<String> selectRemovals(Collection<Entry> entries, int perScopeLimit, int totalLimit) {
        if (perScopeLimit < 1 || totalLimit < 1) {
            throw new IllegalArgumentException("Retention limits must be positive");
        }

        Set<String> removals = new LinkedHashSet<>();
        Map<String, List<Entry>> byScope = new HashMap<>();
        for (Entry entry : entries) {
            byScope.computeIfAbsent(entry.scope(), ignored -> new ArrayList<>()).add(entry);
        }
        Comparator<Entry> oldestFirst = Comparator.comparingLong(Entry::updatedAt)
                .thenComparing(Entry::key);
        for (List<Entry> scoped : byScope.values()) {
            if (scoped.size() <= perScopeLimit) {
                continue;
            }
            scoped.sort(oldestFirst);
            scoped.subList(0, scoped.size() - perScopeLimit)
                    .forEach(entry -> removals.add(entry.key()));
        }

        int remaining = entries.size() - removals.size();
        if (remaining > totalLimit) {
            entries.stream()
                    .filter(entry -> !removals.contains(entry.key()))
                    .sorted(oldestFirst)
                    .limit(remaining - totalLimit)
                    .forEach(entry -> removals.add(entry.key()));
        }
        return Set.copyOf(removals);
    }

    public record Entry(String key, String scope, long updatedAt) {
    }
}
