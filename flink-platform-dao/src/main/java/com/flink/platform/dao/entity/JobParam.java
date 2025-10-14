package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.JobParamType;
import com.flink.platform.common.enums.Status;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job param info. */
@Data
@NoArgsConstructor
@TableName("t_job_param")
public class JobParam implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** user id. */
    private Long userId;

    /** job flow id. */
    private String flowId;

    /** param desc. */
    private String description;

    /** param type. */
    private JobParamType type;

    /** param name. */
    private String paramName;

    /** param value. */
    private String paramValue;

    /** param status. */
    private Status status;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;
}
