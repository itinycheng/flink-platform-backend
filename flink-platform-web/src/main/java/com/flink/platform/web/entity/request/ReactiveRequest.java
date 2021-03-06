package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.LongArrayList;
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

        msg = routeUrlSizeEqOne();
        if (msg != null) {
            return msg;
        }

        return dsIdNotNull();
    }

    public String validateFlink() {
        String msg = subjectNotNull();
        if (msg != null) {
            return msg;
        }

        msg = routeUrlSizeEqOne();
        if (msg != null) {
            return msg;
        }

        msg = deployModeNotNull();
        if (msg != null) {
            return msg;
        }

        msg = execModeNotNull();
        if (msg != null) {
            return msg;
        }

        return versionNotNull();
    }

    public String dsIdNotNull() {
        Long dsId = jobInfo.getConfig().unwrap(SqlJob.class).getDsId();
        return Preconditions.checkNotNull(dsId, "The datasource id cannot be null");
    }

    public String subjectNotNull() {
        return Preconditions.checkNotNull(getSubject(), "The subject cannot be null");
    }

    public String deployModeNotNull() {
        return Preconditions.checkNotNull(getDeployMode(), "The subject cannot be null");
    }

    public String execModeNotNull() {
        return Preconditions.checkNotNull(getExecMode(), "The subject cannot be null");
    }

    public String versionNotNull() {
        return Preconditions.checkNotNull(getVersion(), "The version cannot be null");
    }

    public String routeUrlSizeEqOne() {
        LongArrayList routeUrl = getRouteUrl();
        if (routeUrl == null || routeUrl.size() == 1) {
            return null;
        } else {
            return "The number of Worker must be 1";
        }
    }
}
