package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.AlertType;
import com.flink.platform.dao.entity.alert.BaseAlert;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** Alert configuration. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_alert", autoResultMap = true)
public class AlertInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** alert name. */
    private String name;

    /** user id. */
    private Long userId;

    /** alert type. */
    private AlertType type;

    /** alert desc. */
    private String description;

    /** configuration. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private BaseAlert config;

    /** create time. */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;
}
