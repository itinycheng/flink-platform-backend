package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itiger.persona.command.JobStatistics;
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
 * job run info
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
@TableName("t_job_run_info")
public class JobRunInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * uniqure code
     */
    private Long jobId;

    /**
     * 0: unknown, 1: running, 2: finished, 3: failure
     */
    private Integer status;

    /**
     * sql variables
     * type Map<String, String>
     */
    private String variables;

    /**
     * yarn application id
     */
    private String backInfo;

    /**
     * store json data of {@link JobStatistics}
     */
    private Long resultSize;

    /**
     * submit user
     */
    private String submitUser;

    /**
     * submit time
     */
    private LocalDateTime submitTime;

    /**
     * stop user
     */
    private String stopUser;

    /**
     * stop time
     */
    private LocalDateTime stopTime;

}
