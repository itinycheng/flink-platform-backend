package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Login user. */
@Data
@NoArgsConstructor
@TableName(value = "t_user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String type;

    private String email;

    private String status;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;
}
