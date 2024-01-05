package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.result.JobCallback;
import com.flink.platform.dao.entity.task.BaseJob;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;
import static org.apache.commons.lang3.StringUtils.defaultString;

/** Job run info. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_run", autoResultMap = true)
public class JobRunInfo implements Serializable {

    public static final Set<String> LARGE_FIELDS = Set.of("backInfo", "config", "variables", "subject");

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long jobId;

    private Long flowRunId;

    private Long userId;

    private JobType type;

    private String version;

    private DeployMode deployMode;

    private ExecutionMode execMode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BaseJob config;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    private String subject;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList routeUrl;

    private String host;

    private ExecutionStatus status;

    /** store json data of JobStatistics. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JobCallback backInfo;

    /** submit time. */
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime submitTime;

    /** stop time. */
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime stopTime;

    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;

    @JsonIgnore
    public String getJobCode() {
        return "job_" + jobId;
    }

    public String getDuration() {
        if (submitTime == null || stopTime == null) {
            return EMPTY;
        }

        try {
            Duration duration = Duration.between(submitTime, stopTime);
            return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Estimated data length. <br>
     * The type of back_info column is TEXT and max size of TEXT is 64K.<br>
     * Storing data in HDFS is a better solution.
     */
    public JobCallback getBackInfo() {
        if (backInfo == null) {
            return null;
        }

        int maxLen = 60_000;
        int count = 0;

        // error msg.
        String errMsg = defaultString(backInfo.getErrMsg());
        count = count + errMsg.length();
        if (count >= maxLen) {
            backInfo.setErrMsg(errMsg.substring(0, maxLen));
            backInfo.setMessage(null);
            backInfo.setStdMsg(null);
        }

        // message.
        if (count < maxLen) {
            String message = defaultString(backInfo.getMessage());
            int prevCount = count;
            count = count + message.length();
            if (count >= maxLen) {
                backInfo.setMessage(message.substring(0, maxLen - prevCount));
                backInfo.setStdMsg(null);
            }
        }

        // std msg.
        if (count < maxLen) {
            String stdMsg = defaultString(backInfo.getStdMsg());
            int prevCount = count;
            count = count + stdMsg.length();
            if (count >= maxLen) {
                backInfo.setStdMsg(stdMsg.substring(0, maxLen - prevCount));
            }
        }

        return backInfo;
    }
}
