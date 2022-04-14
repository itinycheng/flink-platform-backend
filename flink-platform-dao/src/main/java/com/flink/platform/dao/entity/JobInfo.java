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

/**
 * job config info. <br>
 * TODO: replace field routeUrl with workerGroup.
 */
@Data
@NoArgsConstructor
@TableName(value = "t_job", autoResultMap = true)
public class JobInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** job name. */
    private String name;

    /** job flow id. */
    private Long flowId;

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
    private Map<String, String> variables;

    /** main content to process. */
    private String subject;

    /**
     * route url. <br>
     * refer to: t_worker
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList routeUrl;

    /** -1: delete, 0: close, 1: open. */
    private JobStatus status;

    /** create time. */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /** json ignore method. */
    @JsonIgnore
    public String getCode() {
        String newName = name.substring(0, 16);
        return "job_" + id + "_" + newName;
    }
}
