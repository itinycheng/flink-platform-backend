package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import com.flink.platform.common.enums.WorkerStatus;
import com.flink.platform.common.environment.EnvironmentSpec;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

import static com.flink.platform.common.constants.Constant.HEARTBEAT_TIMEOUT;

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

    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<EnvironmentSpec> environments;

    @Setter(AccessLevel.NONE)
    @TableField(update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime updateTime;

    @Setter(AccessLevel.NONE)
    private LocalDateTime createTime;

    public boolean isActive() {
        if (this.heartbeat == null) {
            return false;
        }

        long timeout = System.currentTimeMillis() - this.heartbeat;
        return timeout < HEARTBEAT_TIMEOUT;
    }
}
