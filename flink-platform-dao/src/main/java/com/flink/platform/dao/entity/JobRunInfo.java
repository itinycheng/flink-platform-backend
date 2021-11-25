package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.DeployMode;
import com.flink.platform.common.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job run info. */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_job_run_info")
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private Long jobId;

    private JobType jobType;

    private String jobVersion;

    private DeployMode jobDeployMode;

    private String jobRouteUrl;

    private Long flowRunId;

    private Integer status;

    /** sql variables type `Map[String, String]`. */
    private String variables;

    /** yarn application id. */
    private String backInfo;

    /** store json data of JobStatistics. */
    private Long resultSize;

    /** submit user. */
    private String submitUser;

    /** submit time. */
    private LocalDateTime submitTime;

    /** stop user. */
    private String stopUser;

    /** stop time. */
    private LocalDateTime stopTime;
}
