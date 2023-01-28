package com.flink.platform.dao.entity.task;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Shell job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShellJob extends BaseJob {

    private String timeout;
}
