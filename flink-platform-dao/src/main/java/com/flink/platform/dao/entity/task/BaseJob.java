package com.flink.platform.dao.entity.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.flink.platform.common.enums.JobType;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/** base job. */
@Data
@NoArgsConstructor
@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = FlinkJob.class, name = "FLINK_JAR"),
    @JsonSubTypes.Type(value = FlinkJob.class, name = "FLINK_SQL"),
    @JsonSubTypes.Type(value = JavaJob.class, name = "COMMON_JAR"),
    @JsonSubTypes.Type(value = SqlJob.class, name = "CLICKHOUSE_SQL"),
    @JsonSubTypes.Type(value = ShellJob.class, name = "SHELL"),
    @JsonSubTypes.Type(value = ConditionJob.class, name = "CONDITION"),
    @JsonSubTypes.Type(value = DependentJob.class, name = "DEPENDENT"),
})
public class BaseJob {

    private JobType type;

    @JsonIgnore
    public <T> T unwrap(Class<T> clazz) {
        return clazz.isInstance(this) ? clazz.cast(this) : null;
    }
}
