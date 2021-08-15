package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itiger.persona.common.enums.ExecutionMode;
import com.itiger.persona.enums.DeployMode;
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
 * job modify info
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
@TableName("t_job_history")
public class JobHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * job info id
     */
    private String jobId;

    /**
     * job name
     */
    private String jobName;

    /**
     * job desc
     */
    private String jobDesc;

    /**
     * job type
     */
    private String jobType;

    /**
     * config for run job
     */
    private String jobConfig;

    /**
     * deploy mode
     */
    private DeployMode deployMode;

    /**
     * execution mode
     */
    private ExecutionMode execMode;

    /**
     * cron expression
     */
    private String cronExpr;

    /**
     * sql or jar path
     */
    private String subject;

    /**
     * catalog id list
     */
    private String catalogs;

    /**
     * external jars
     */
    private String extJars;

    /**
     * main args
     */
    private String mainArgs;

    /**
     * main class
     */
    private String mainClass;

    /**
     * -1: delete, 0: close, 1: open
     */
    private Integer status;

    /**
     * modify user
     */
    private String modifyUser;

    /**
     * modify time
     */
    private LocalDateTime modifyTime;

}
