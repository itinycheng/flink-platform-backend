package com.flink.platform.web.command;

import com.flink.platform.common.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * job command
 *
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClickhouseCommand implements JobCommand {

    private List<String> sqls;

    /**
     * build a command
     */
    @Override
    public String toCommandString() {
        return JsonUtil.toJsonString(sqls);
    }

}
