package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.common.enums.UserStatus;
import com.flink.platform.common.model.UserRoles;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Login user. */
@Data
@NoArgsConstructor
@TableName(value = "t_user", autoResultMap = true)
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private String externalId;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private LongArrayList workers;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private UserRoles roles;

    private UserStatus status;

    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;
}
