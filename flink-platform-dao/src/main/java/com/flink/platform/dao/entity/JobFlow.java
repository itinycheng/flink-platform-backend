package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.flink.platform.common.enums.JobFlowStatus;
import com.flink.platform.common.graph.DAG;
import com.flink.platform.common.model.JobEdge;
import com.flink.platform.common.model.JobVertex;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** job flow. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName(value = "t_job_flow", autoResultMap = true)
public class JobFlow {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** unique code. */
    private String code;

    /** flow name. */
    private String name;

    /** user id. */
    private Long userId;

    /** job flow description. */
    private String description;

    /** crontab. */
    private String cronExpr;

    /** job flow json. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private DAG<Long, JobVertex, JobEdge> flow;

    /** version, use for DAG backward compatible? */
    private String version;

    private Integer priority;

    /** email receivers. */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> receivers;

    /** status. */
    private JobFlowStatus status;

    /** create time. */
    private LocalDateTime createTime;

    /** update time. */
    private LocalDateTime updateTime;
}
