package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itiger.persona.common.job.ExecutionMode;
import com.itiger.persona.enums.DeployMode;
import com.itiger.persona.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * job config info
 * </p>
 *
 * @author shik
 * @since 2021-04-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("t_job_info")
public class JobInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * uniqure code
     */
    private String jobCode;

    /**
     * job name
     */
    private String jobName;

    /**
     * job desc
     */
    private String jobDesc;

    /**
     * flink-sql, flink-jar, common-jar
     */
    private JobType jobType;

    /**
     * config for run job
     */
    private String config;

    /**
     * deploy mode: run-local, pre-yarn, yarn-session, run-application, etc.
     */
    private DeployMode deployMode;

    /**
     * BATCH, STREAMING
     */
    private ExecutionMode execMode;

    /**
     * sql, runable jar path
     */
    private String subject;

    /**
     * main args
     */
    private String mainArgs;

    /**
     * main class
     */
    private String mainClass;

    /**
     * catalogs
     */
    private String catalogs;

    /**
     * external jars
     */
    private String extJars;

    /**
     * -1: delete, 0: close, 1: open
     */
    private Integer status;

    /**
     * cron expression for the job
     */
    private String cronExpr;

    private String createUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private String updateUser;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

}
