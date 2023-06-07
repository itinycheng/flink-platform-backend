package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.dao.entity.alert.AlertConfigList;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** job flow. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_job_flow", autoResultMap = true)
public class JobFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private String code;

    /** flow name. */
    private String name;

    /** user id. */
    private Long userId;

    /** job flow description. */
    private String description;

    /** crontab. */
    private String cronExpr;

    /** job flow json. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JobFlowDag flow;

    private Integer priority;

    /** alert strategy. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AlertConfigList alerts;

    /** status. */
    private JobFlowStatus status;

    /** create time. */
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;

    /** update time. */
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime updateTime;
}
