package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

import static com.flink.platform.common.constants.Constant.HEARTBEAT_TIMEOUT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_DATE_TIME_FORMAT;
import static com.flink.platform.common.util.DateUtil.GLOBAL_TIMEZONE;

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

    private Integer grpcPort;

    @TableField("status")
    private WorkerStatus role;

    private Long heartbeat;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    @Setter(AccessLevel.NONE)
    @JsonFormat(pattern = GLOBAL_DATE_TIME_FORMAT, timezone = GLOBAL_TIMEZONE)
    private LocalDateTime createTime;

    public boolean isActive() {
        if (this.heartbeat == null) {
            return false;
        }

        long timeout = System.currentTimeMillis() - this.heartbeat;
        return timeout < HEARTBEAT_TIMEOUT;
    }
}
