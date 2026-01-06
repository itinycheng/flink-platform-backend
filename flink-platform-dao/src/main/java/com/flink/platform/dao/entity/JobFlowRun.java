package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import com.flink.platform.dao.handler.Jackson3TypeHandler;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static com.flink.platform.common.constants.Constant.EMPTY;

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

    private JobFlowType type;

    private String cronExpr;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private JobFlowDag flow;

    private String host;

    private Integer priority;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private ExecutionConfig config;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private StringArrayList tags;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private AlertConfigList alerts;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Timeout timeout;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private Map<String, Object> params;

    private ExecutionStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    public String getDuration() {
        if (startTime == null || endTime == null) {
            return EMPTY;
        }

        Duration duration = Duration.between(startTime, endTime);
        return DurationFormatUtils.formatDuration(duration.toMillis(), "HH:mm:ss");
    }
}
