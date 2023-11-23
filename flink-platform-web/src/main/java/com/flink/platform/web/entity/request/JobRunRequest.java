package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.util.List;

/** Job request info. */
@Getter
@NoArgsConstructor
public class JobRunRequest {

    @Delegate
    private final JobRunInfo jobInfo = new JobRunInfo();

    @Setter
    private List<Long> jobIds;
}
