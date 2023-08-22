package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.DbType;
import com.flink.platform.dao.entity.ds.DatasourceParam;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

/** Datasource entity. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_datasource", autoResultMap = true)
public class Datasource {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** name. */
    private String name;

    /** description. */
    private String description;

    /** user id. */
    private Long userId;

    /** database type. */
    private DbType type;

    /** connection parameters. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private DatasourceParam params;

    /** create time. */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;
}
