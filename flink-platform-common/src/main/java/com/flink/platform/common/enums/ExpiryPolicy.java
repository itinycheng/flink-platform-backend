package com.flink.platform.common.enums;

/**
 * When an upstream's latest run stops counting for a job flow dependency.
 *
 * <p>Status matching alone is not enough: it is checked against whichever run happens to be the latest, so an
 * upstream that silently stopped being scheduled would keep satisfying its downstreams forever on a run from weeks
 * ago. Expiry is what turns "the last run succeeded" into "the last run succeeded recently enough to be the one
 * being asked about".
 */
public enum ExpiryPolicy {

    /**
     * Expires once the upstream's own cron is due again, so the window follows the upstream's schedule with no
     * maintenance. Cannot be evaluated when the upstream has no cron.
     */
    UPSTREAM_SCHEDULE,

    /**
     * Expires after the dependency's own {@code duration}, independent of the upstream's schedule. Use when the
     * downstream's tolerance differs from the upstream's period, e.g. a minute-level upstream a downstream is
     * willing to accept within the hour.
     */
    FIXED_DURATION,

    /**
     * Never expires: the latest run counts however old it is. For one-shot upstreams that have no schedule to
     * follow. Note this gives up the "upstream stopped running" detection the other two provide.
     */
    NEVER
}
