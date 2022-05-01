package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.task.SqlJob;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;

/** user request. */
@Data
@NoArgsConstructor
public class ReactiveRequest {

    @Getter @Delegate private final JobInfo jobInfo = new JobInfo();

    private String[] envProps;

    public String validateSql() {
        String msg = subjectNotNull();
        if (msg != null) {
            return msg;
        }

        return dsIdNotNull();
    }

    public String dsIdNotNull() {
        Long dsId = jobInfo.getConfig().unwrap(SqlJob.class).getDsId();
        return Preconditions.checkNotNull(dsId, "The datasource id cannot be null");
    }

    public String subjectNotNull() {
        return Preconditions.checkNotNull(getSubject(), "The subject cannot be null");
    }
}
