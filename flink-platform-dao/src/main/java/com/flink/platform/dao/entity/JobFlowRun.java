package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.common.enums.ExecutionStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** job flow instance. */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_job_flow_run", autoResultMap = true)
public class JobFlowRun {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private Long flowId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private DAG<Long, JobVertex, JobEdge> flow;

    private String version;

    private String host;

    private Integer priority;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> receivers;

    private ExecutionStatus status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;
}
