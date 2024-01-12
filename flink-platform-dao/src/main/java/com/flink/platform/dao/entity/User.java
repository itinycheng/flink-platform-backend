package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.UserType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** Login user. */
@Data
@NoArgsConstructor
@TableName(value = "t_user", autoResultMap = true)
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private UserType type;

    private String email;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList workers;

    private String status;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;
}
