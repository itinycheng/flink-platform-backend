package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.flink.platform.common.enums.ExecutionStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** job flow instance. */
@Data
@NoArgsConstructor
@TableName(value = "t_job_flow_run", autoResultMap = true)
public class JobFlowRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long flowId;

    private Long userId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JobFlowDag flow;

    private String host;

    private Integer priority;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private LongArrayList alerts;

    private ExecutionStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
