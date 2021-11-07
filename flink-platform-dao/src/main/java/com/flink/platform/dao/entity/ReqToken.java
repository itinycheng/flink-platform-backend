package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** Request token. */
@Data
@EqualsAndHashCode(callSuper = false)
public class ReqToken implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 请求token. */
    private String token;

    /** 状态. */
    private Boolean status;

    /** 用户. */
    private String username;

    private Long createTime;

    private Long updateTime;
}
