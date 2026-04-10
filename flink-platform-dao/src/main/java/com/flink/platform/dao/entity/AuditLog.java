package com.flink.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.flink.platform.common.enums.EntityType;
import com.flink.platform.common.enums.OperationType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Audit log: records full entity snapshots for INSERT / UPDATE / DELETE. */
@Data
@NoArgsConstructor
@TableName(value = "t_audit_log", autoResultMap = true)
public class AuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** primary key of the audited entity. */
    private Long entityId;

    /** logical entity type, e.g. JOB, JOB_FLOW. */
    private EntityType entityType;

    /** INSERT, UPDATE, or DELETE. */
    private OperationType operation;

    /** full JSON snapshot of the entity state after the operation (or before, for DELETE). */
    private String snapshot;

    /** user who made the change; null for system-triggered operations. */
    private Long operatorId;

    @Setter(AccessLevel.NONE)
    private LocalDateTime operateTime;
}
