package com.itiger.persona.flink.udf;

import com.itiger.persona.flink.udf.util.ObjectUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.functions.ScalarFunction;

import java.util.Arrays;
import java.util.List;

import static com.itiger.persona.common.constants.Constant.AND;
import static com.itiger.persona.common.constants.Constant.OR;

/**
 * @author tiny.wang
 */
public class ListContainsValueFunction extends ScalarFunction {

    public boolean eval(List<Object> columnValues, String inputValue) {
        if (CollectionUtils.isEmpty(columnValues)
                || StringUtils.isBlank(inputValue)) {
            return false;
        }
        // and | or operator
        boolean orOperator = inputValue.contains(OR);
        String[] inputItems;
        if (orOperator) {
            inputItems = inputValue.split(OR);
        } else {
            inputItems = inputValue.split(AND);
        }
        return orOperator ?
                isAnyContained(columnValues, inputItems) :
                isAllContained(columnValues, inputItems);
    }

    private boolean isAllContained(List<Object> columnValues, String[] inputItems) {
        Object[] cast = ObjectUtil.cast(inputItems, columnValues.get(0).getClass());
        return Arrays.stream(cast).allMatch(columnValues::contains);
    }

    private boolean isAnyContained(List<Object> columnValues, String[] inputItems) {
        Object[] cast = ObjectUtil.cast(inputItems, columnValues.get(0).getClass());
        return Arrays.stream(cast).anyMatch(columnValues::contains);
    }

}
