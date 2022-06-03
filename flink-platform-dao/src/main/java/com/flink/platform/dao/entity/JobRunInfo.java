package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.enums.JobType;
import com.flink.platform.dao.entity.task.BaseJob;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

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

    private ExecutionStatus status;

    /** store json data of JobStatistics. */
    private String backInfo;

    /** submit time. */
    private LocalDateTime submitTime;

    /** stop time. */
    private LocalDateTime stopTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
