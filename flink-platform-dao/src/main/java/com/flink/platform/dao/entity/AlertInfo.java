package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.AlertType;
import com.flink.platform.dao.entity.alert.BaseAlert;
import com.flink.platform.dao.handler.Jackson3TypeHandler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Alert configuration. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_alert", autoResultMap = true)
public class AlertInfo implements Serializable {

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
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private BaseAlert config;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;
}
