package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** job flow instance. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_flow_run", autoResultMap = true)
public class JobFlowRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long flowId;

    private Long userId;

    private String cronExpr;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JobFlowDag flow;

    private String host;

    private Integer priority;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ExecutionConfig config;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private StringArrayList tags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private AlertConfigList alerts;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Timeout timeout;

    private ExecutionStatus status;

    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime startTime;

    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime endTime;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;

    public String getDuration() {
        if (startTime == null || endTime == null) {
            return EMPTY;
        }

        Duration duration = Duration.between(startTime, endTime);
        return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss");
    }
}
