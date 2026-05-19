package com.flink.platform.dao.view;

import com.flink.platform.dao.entity.JobFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** job flow with related columns from joined tables. */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobFlowDetails extends JobFlow {

    /** username from t_user. */
    private String username;
}
