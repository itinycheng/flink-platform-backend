package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.common.enums.Status;
import com.flink.platform.dao.entity.config.WorkspaceConfig;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Workspace entity. */
@Data
@NoArgsConstructor
@TableName(value = "t_workspace", autoResultMap = true)
public class Workspace {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private WorkspaceConfig config;

    private Status status;

    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;
}
