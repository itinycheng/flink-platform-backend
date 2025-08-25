package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.Status;
import com.flink.platform.common.enums.TagType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** tag info. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_tag")
public class TagInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private String code;

    /** flow name. */
    private String name;

    /** user id. */
    private Long userId;

    /** type. */
    private TagType type;

    /** status. */
    private Status status;

    /** create time. */
    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    /** update time. */
    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;
}
