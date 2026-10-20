package com.constructflow.common.exception;

/**
 * Compares the version the client last saw with the current entity version. JPA's {@code @Version}
 * protects concurrent transactions; this check protects the longer "read in browser, edit, save later"
 * window, where the second save would otherwise silently overwrite the first.
 */
public final class OptimisticLock {

    private OptimisticLock() {
    }

    public static void check(Long expectedVersion, Long currentVersion, String entityName) {
        if (expectedVersion == null || !expectedVersion.equals(currentVersion)) {
            throw new ConflictException("STALE_VERSION",
                    entityName + " was changed by someone else. Reload and try again.");
        }
    }
}
