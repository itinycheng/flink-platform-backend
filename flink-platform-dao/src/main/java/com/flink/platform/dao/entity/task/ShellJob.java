package com.flink.platform.dao.entity.task;

import com.flink.platform.common.util.DurationUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/** Shell job. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShellJob extends BaseJob {

    private String timeout;

    public Duration parseTimeout() {
        if (StringUtils.isNotEmpty(timeout)) {
            try {
                return DurationUtil.parse(timeout);
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
