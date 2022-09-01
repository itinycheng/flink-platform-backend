package com.flink.platform.web.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** reactive vo. */
@Data
@AllArgsConstructor
public class ReactiveVo {

    protected final boolean sync;

    protected final String execId;
}
