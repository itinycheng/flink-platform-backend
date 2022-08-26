package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

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

    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime lastLoginTime;
}
