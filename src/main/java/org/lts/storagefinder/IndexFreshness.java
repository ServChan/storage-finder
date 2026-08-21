package org.lts.storagefinder;

public enum IndexFreshness {
    FRESH,
    AGING,
    STALE,
    FRAME_ONLY;

    public static final long FRESH_MILLIS = 24L * 60L * 60L * 1_000L;
    public static final long AGING_MILLIS = 7L * 24L * 60L * 60L * 1_000L;

    public static IndexFreshness classify(long updatedAt, long now) {
        long age = Math.max(0L, now - updatedAt);
        if (age <= FRESH_MILLIS) {
            return FRESH;
        }
        return age <= AGING_MILLIS ? AGING : STALE;
    }
}
