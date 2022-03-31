package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.AlertType;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** Ding ding alert. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class DingDingAlert extends BaseAlert {}

    /** Fei shu alert. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class FeiShuAlert extends BaseAlert {

        private String webhook;

        private String content;
    }

    /** Email alert. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class EmailAlert extends BaseAlert {}

    /** Sms alert. */
    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class SmsAlert extends BaseAlert {}
}
