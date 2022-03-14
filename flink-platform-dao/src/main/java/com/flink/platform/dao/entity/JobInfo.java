package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.JobStatus;
import com.flink.platform.common.enums.JobType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** job config info. */
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

    /** configs for run job. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> configs;

    /** deploy mode. */
    private DeployMode deployMode;

    /** execution mode. */
    private ExecutionMode execMode;

    /** variables for `subject`. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> variables;

    /** sql or jar path. */
    private String subject;

    /** catalogs. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList catalogs;

    /** external jars. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> extJars;

    /** main args. */
    private String mainArgs;

    /** main class. */
    private String mainClass;

    /**
     * route url. <br>
     * example: http://127.0.0.1
     */
    private String routeUrl;

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

    public String getCode() {
        return "job_" + id;
    }
}
