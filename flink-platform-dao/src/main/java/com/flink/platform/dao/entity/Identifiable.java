package com.flink.platform.dao.entity;

/** Entities that can be audited must expose their primary key. */
public interface Identifiable {
    Long getId();
}
