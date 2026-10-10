package com.flink.platform.web.dto.request;

import com.flink.platform.dao.entity.ExecutionConfig;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.time.LocalDateTime;

/** Job flow execution request info. */
@Getter
@Setter
@NoArgsConstructor
public class JobFlowExecRequest {

    @Delegate
    private ExecutionConfig config = new ExecutionConfig();

    private LocalDateTime scheduleTime;
}
