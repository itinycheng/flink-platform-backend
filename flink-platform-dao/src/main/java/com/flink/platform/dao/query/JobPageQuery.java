package com.flink.platform.dao.query;

import com.flink.platform.common.enums.JobStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;

/** query parameters for paging job details. */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobPageQuery extends BasePageQuery {

    private Long id;

    private Long flowId;

    private String name;

    private JobStatus status;

    @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT)
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = GLOBAL_DATE_TIME_FORMAT)
    private LocalDateTime endTime;

    /** attach the last run of each job. */
    private boolean includeJobRuns;

    /** drop the jobs already placed in the dag of {@link #flowId}. */
    private boolean excludeJobsInFlow;

    /** resolved from the dag of {@link #flowId} when {@link #excludeJobsInFlow} is set. */
    private List<Long> excludeJobIds;

    public boolean isSortByIdDesc() {
        return "-id".equals(getSort());
    }

    public boolean hasTimeRange() {
        return startTime != null && endTime != null;
    }
}
