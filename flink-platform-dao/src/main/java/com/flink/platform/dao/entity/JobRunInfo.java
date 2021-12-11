package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.DeployMode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job run info. */
@Data
@NoArgsConstructor
@TableName("t_job_run_info")
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private Long jobId;

    private DeployMode deployMode;

    private String routeUrl;

    private Long flowRunId;

    private String command;

    private Integer status;

    /** sql variables type `Map[String, String]`. */
    private String variables;

    /** yarn application id. */
    private String backInfo;

    /** store json data of JobStatistics. */
    private Long resultSize;

    /** submit user. */
    @Deprecated private String submitUser;

    /** submit time. */
    private LocalDateTime submitTime;

    /** stop user. */
    @Deprecated private String stopUser;

    /** stop time. */
    private LocalDateTime stopTime;

    private LocalDateTime createTime;
}
