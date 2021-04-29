package com.itiger.persona.flink.udf;

import com.itiger.persona.common.util.JsonUtil;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.functions.ScalarFunction;

/**
 * to string
 *
 * @author tiny.wang
 */
public class ToStringFunction extends ScalarFunction {

    public String eval(@DataTypeHint(inputGroup = InputGroup.ANY) Object value) {
        return JsonUtil.toJsonString(value);
    }
}
