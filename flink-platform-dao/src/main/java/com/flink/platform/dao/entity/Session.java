package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** User login session. */
@Data
@NoArgsConstructor
@TableName("t_user_session")
public class Session {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String token;

    private long userId;

    private String ip;

    private LocalDateTime lastLoginTime;
}
