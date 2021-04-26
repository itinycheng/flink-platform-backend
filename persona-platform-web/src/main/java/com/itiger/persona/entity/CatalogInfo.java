package com.itiger.persona.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.itiger.persona.common.enums.CatalogType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * job catalog info
 * </p>
 *
 * @author shik
 * @since 2021-04-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("t_catalog_info")
public class CatalogInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * catalog name
     */
    private String name;

    /**
     * catalog type
     */
    private CatalogType type;

    /**
     * default database
     */
    private String defaultDatabase;

    /**
     * config dir path
     */
    private String configPath;

    /**
     * config properties
     */
    private String configs;

    private String createUser;

    /**
     * create time
     */
    private LocalDateTime createTime;


}
