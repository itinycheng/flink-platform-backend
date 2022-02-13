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
@TableName(value = "t_job_info")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private long id;

    private String userName;

    private String password;

    private String email;

    private String phone;

    /** user specified queue. */
    private String queue;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
