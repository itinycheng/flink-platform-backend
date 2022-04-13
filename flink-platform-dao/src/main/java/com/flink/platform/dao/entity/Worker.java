package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.WorkerStatus;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Worker instance. */
@Data
@NoArgsConstructor
@TableName(value = "t_worker")
public class Worker {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("`desc`")
    private String desc;

    private String ip;

    private String port;

    private WorkerStatus status;

    private Long heartbeat;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
