package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.enums.JobFlowType;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

import static com.flink.platform.common.enums.JobFlowType.JOB_FLOW;
import static com.flink.platform.common.enums.JobFlowType.SUB_FLOW;

/** job flow. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_job_flow", autoResultMap = true)
public class JobFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private String code;

    /** flow name. */
    private String name;

    /** user id. */
    private Long userId;

    /** job flow description. */
    private String description;

    private JobFlowType type;

    /** crontab. */
    private String cronExpr;

    /** job flow json. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JobFlowDag flow;

    private Integer priority;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private ExecutionConfig config;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private StringArrayList tags;

    /** alert strategy. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AlertConfigList alerts;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Timeout timeout;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sharedVars;

    /** status. */
    private JobFlowStatus status;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    /** update time. */
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    public boolean isWorkflow() {
        return this.type == JOB_FLOW || this.type == SUB_FLOW;
    }
}
