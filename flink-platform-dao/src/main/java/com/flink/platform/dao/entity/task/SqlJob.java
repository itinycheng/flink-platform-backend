package com.flink.platform.dao.entity.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** sql job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SqlJob extends BaseJob {
    /** datasource id. */
    private Long dsId;
}
