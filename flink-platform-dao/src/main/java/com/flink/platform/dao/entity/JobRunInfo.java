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
import com.flink.platform.dao.entity.task.BaseJob;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** Job run info. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_run", autoResultMap = true)
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String backInfo;

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

        Duration duration = Duration.between(submitTime, stopTime);
        return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss");
    }
}
