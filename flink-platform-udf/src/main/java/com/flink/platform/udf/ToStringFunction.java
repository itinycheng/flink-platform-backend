package com.flink.platform.udf;

import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.functions.ScalarFunction;

import com.flink.platform.common.util.JsonUtil;

/** to string. */
public class ToStringFunction extends ScalarFunction {

    public String eval(@DataTypeHint(inputGroup = InputGroup.ANY) Object value) {
        return JsonUtil.toJsonString(value);
    }
}
