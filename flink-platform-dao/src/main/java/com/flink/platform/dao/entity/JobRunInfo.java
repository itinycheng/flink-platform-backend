package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.ExecutionMode;
import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job run info. */
@Data
@NoArgsConstructor
@TableName("t_job_run")
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long jobId;

    private Long flowRunId;

    private DeployMode deployMode;

    private ExecutionMode execMode;

    private String routeUrl;

    private String subject;

    private ExecutionStatus status;

    /** sql variables type `Map[String, String]`. */
    private String variables;

    /** store json data of JobStatistics. */
    private String backInfo;

    /** submit time. */
    private LocalDateTime submitTime;

    /** stop time. */
    private LocalDateTime stopTime;

    private LocalDateTime createTime;
}
