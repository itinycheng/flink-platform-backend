package com.flink.platform.web.entity.request;

import com.flink.platform.common.util.DurationUtil;
import com.flink.platform.dao.entity.JobInfo;
import com.flink.platform.dao.entity.task.BaseJob;
import com.flink.platform.dao.entity.task.ShellJob;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.regex.Pattern;

import static com.flink.platform.common.enums.JobType.CONDITION;
import static com.flink.platform.common.enums.JobType.DEPENDENT;
import static com.flink.platform.common.util.Preconditions.checkNotNull;

/** Job request info. */
@NoArgsConstructor
public class JobInfoRequest {

    private static final String JOB_NAME_REGEX = "^[a-zA-Z][a-zA-Z0-9._-]{5,64}$";

    private static final Pattern JOB_NAME_PATTERN = Pattern.compile(JOB_NAME_REGEX);

    @Getter @Delegate private final JobInfo jobInfo = new JobInfo();

    public String validateOnCreate() {
        String msg = verifyName();
        if (msg != null) {
            return msg;
        }

        msg = verifyType();
        if (msg != null) {
            return msg;
        }

        msg = verifyExecMode();
        if (msg != null) {
            return msg;
        }

        msg = verifySubject();
        if (msg != null) {
            return msg;
        }

        msg = verifyConfig();
        return msg;
    }

    public String validateOnUpdate() {
        String msg = verifyId();
        if (msg != null) {
            return msg;
        }

        if (getName() != null) {
            msg = verifyName();
            if (msg != null) {
                return msg;
            }
        }

        msg = verifyCreateTime();
        if (msg != null) {
            return msg;
        }

        msg = verifyUpdateTime();
        if (msg != null) {
            return msg;
        }

        msg = verifyConfig();
        return msg;
    }

    public String verifyId() {
        String errorMsg = null;
        if (getId() == null) {
            errorMsg = "The id of Job cannot be null";
        }
        return errorMsg;
    }

    public String verifyName() {
        String errorMsg = null;
        if (getName() == null) {
            errorMsg = "The name of Job cannot be null";
        } else if (!JOB_NAME_PATTERN.matcher(getName()).matches()) {
            errorMsg = String.format("invalid job name, regex: `%s`", JOB_NAME_REGEX);
        }
        return errorMsg;
    }

    public String verifyType() {
        return checkNotNull(getType(), "The job type cannot be null");
    }

    public String verifyExecMode() {
        return checkNotNull(getExecMode(), "The job execution type cannot be null");
    }

    public String verifySubject() {
        if (getType() == CONDITION || getType() == DEPENDENT) {
            setSubject("");
        }
        return checkNotNull(getSubject(), "The job subject cannot be null");
    }

    private String verifyCreateTime() {
        String errorMsg = null;
        if (getCreateTime() != null) {
            errorMsg = "The create time of job must be null";
        }
        return errorMsg;
    }

    private String verifyUpdateTime() {
        String errorMsg = null;
        if (getUpdateTime() != null) {
            errorMsg = "The update time of job must be null";
        }
        return errorMsg;
    }

    private String verifyConfig() {
        String errorMsg = null;
        BaseJob config = getConfig();
        ShellJob shellJob = config.unwrap(ShellJob.class);
        if (shellJob != null) {
            String timeout = shellJob.getTimeout();
            if (!NumberUtils.isCreatable(timeout) && !DurationUtil.ableToParse(timeout)) {
                errorMsg = "The timeout of ShellJob is invalid";
            }
        }

        return errorMsg;
    }
}
