package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.JobType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** job config info. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_info", autoResultMap = true)
public class JobInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    @Deprecated private String code;

    /** job name. */
    private String name;

    /** job desc. */
    private String description;

    /** job type. */
    private JobType type;

    /** version. */
    private String version;

    /** config for run job. */
    private String config;

    /** deploy mode. */
    private DeployMode deployMode;

    /** execution mode. */
    private ExecutionMode execMode;

    /** variables for `subject`. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> variables;

    /** sql or jar path. */
    private String subject;

    /** use json to explain sql logic in subject column. */
    private String sqlPlan;

    /** main args. */
    private String mainArgs;

    /** main class. */
    private String mainClass;

    /** catalogs. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList catalogs;

    /** external jars. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> extJars;

    /** -1: delete, 0: close, 1: open. */
    private Integer status;

    /** cron expression for the job. */
    @Deprecated private String cronExpr;

    /**
     * route url. <br>
     * example: http://127.0.0.1
     */
    private String routeUrl;

    /** create user. */
    @Deprecated private String createUser;

    /** create time. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** update user. */
    @Deprecated private String updateUser;

    /** update time. */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
