package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.CatalogType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job catalog info. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("t_catalog_info")
public class CatalogInfo implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** catalog name. */
    private String name;

    /** catalog type. */
    private CatalogType type;

    /** catalog desc. */
    private String description;

    /** user id. */
    private Long userId;

    /** default database. */
    @Deprecated
    private String defaultDatabase;

    /** config dir path. */
    @Deprecated
    private String configPath;

    /** config properties. */
    @Deprecated
    private String configs;

    /** catalog create sql. */
    private String createSql;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;
}
