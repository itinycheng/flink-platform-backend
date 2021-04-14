package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
     * flink-sql, flink-jar, common-jar
     */
    private String jobType;

    /**
     * config for run job
     */
    private String jobConfig;

    /**
     * deploy mode: run-local, pre-yarn, yarn-session, run-application, etc.
     */
    private String deployMode;

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
     * external jar
     */
    private String extJar;

    /**
     * -1: delete, 0: close, 1: open
     */
    private Integer status;

    private String modifyUser;

    /**
     * 创建时间
     */
    private LocalDateTime modifyTime;


}
