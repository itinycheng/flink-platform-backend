package com.flink.platform.dao.entity;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.flink.platform.common.enums.ExecutionCondition;
import com.flink.platform.common.enums.UnmatchedPolicy;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import static com.flink.platform.common.enums.ExecutionCondition.ALL_MATCHED;
import static com.flink.platform.common.enums.UnmatchedPolicy.WAIT;

/**
 * The dependencies of a job flow.
 */
@Data
@NoArgsConstructor
public class DependencyConfig {

    private List<Dependency> dependencies;

    @JsonSetter(nulls = Nulls.SKIP)
    private ExecutionCondition precondition = ALL_MATCHED;

    @JsonSetter(nulls = Nulls.SKIP)
    private UnmatchedPolicy onUnmatched = WAIT;

    private transient boolean latestResolved;

    public boolean hasDependencies() {
        return CollectionUtils.isNotEmpty(dependencies);
    }
}
