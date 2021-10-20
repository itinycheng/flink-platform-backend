package com.flink.platform.web.entity.request;

import com.flink.platform.web.parser.SqlSelect;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;

/** User group request. */
@Data
@NoArgsConstructor
@EqualsAndHashCode
public class UserGroupRequest {

    /** job run id. */
    private Long runId;

    /** job id. */
    private Long id;

    private String name;

    private String description;

    private String cronExpr;

    private String createUser;

    private String updateUser;

    private SqlSelect select;

    public void setCronExpr(String cronExpr) {
        String expr;
        if (StringUtils.isNotBlank(cronExpr)) {
            expr = cronExpr;
        } else {
            Random random = new Random();
            int second = random.nextInt(60);
            int minute = random.nextInt(60);
            int hour = random.nextInt(9);
            expr = String.format("%d %d %d * * ?", second, minute, hour);
        }
        this.cronExpr = expr;
    }
}
