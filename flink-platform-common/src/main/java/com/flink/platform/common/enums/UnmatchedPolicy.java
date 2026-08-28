package com.flink.platform.common.enums;

/** What a waiting job flow run does once an upstream has finished on a status its dependency doesn't accept. */
public enum UnmatchedPolicy {

    /**
     * Keep waiting until the deadline. The latest run says nothing about the next one: a cron upstream fires again,
     * and a failed one is routinely re-run by hand, in which case the downstream picks it up on its own.
     */
    WAIT,

    /** Give up at once. Use when a settled upstream means the downstream can't produce anything useful anyway. */
    FAIL
}
