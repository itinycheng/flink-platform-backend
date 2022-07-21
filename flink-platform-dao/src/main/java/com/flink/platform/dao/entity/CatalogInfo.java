package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.CatalogType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Job catalog info. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("t_catalog_info")
public class CatalogInfo implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String defaultDatabase;

    /** config dir path. */
    private String configPath;

    /** config properties. */
    private String configs;

    /** catalog create sql. */
    private String createSql;

    /** create time. */
    private LocalDateTime createTime;

    /** update time. */
    private LocalDateTime updateTime;
}
