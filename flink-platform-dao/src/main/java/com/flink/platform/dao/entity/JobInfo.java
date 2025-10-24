package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.task.BaseJob;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * job config info. <br>
 * TODO: replace field routeUrl with workerGroup.
 */
@Data
@NoArgsConstructor
@TableName(value = "t_job", autoResultMap = true)
public class JobInfo implements Serializable {

    public static final Set<String> LARGE_FIELDS = Set.of("config", "variables", "subject");

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** job name. */
    private String name;

    /** job flow id. */
    private Long flowId;

    /** user id. */
    private Long userId;

    /** job desc. */
    private String description;

    /** job type. */
    private JobType type;

    /** version. */
    private String version;

    /** deploy mode. */
    private DeployMode deployMode;

    /** execution mode. */
    private ExecutionMode execMode;

    /** configuration. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private BaseJob config;

    /** variables for `subject`. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    /** main content to process. */
    private String subject;

    /**
     * route url. <br> refer to: t_worker
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList routeUrl;

    /** -1: delete, 0: close, 1: open. */
    private JobStatus status;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    /** job run id. */
    @TableField(exist = false)
    private Long jobRunId;

    /**
     * flow run id.
     */
    @TableField(exist = false)
    private Long flowRunId;

    /**
     * job run status.
     */
    @TableField(exist = false)
    private ExecutionStatus jobRunStatus;
}
