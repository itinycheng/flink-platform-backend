package com.flink.platform.web.entity.request;

import com.flink.platform.dao.entity.JobRunInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;

import java.util.List;

/** Job request info. */
@NoArgsConstructor
public class JobRunRequest {

    @Getter @Delegate private final JobRunInfo jobInfo = new JobRunInfo();

    @Getter @Setter private List<Long> jobIds;
}
