package com.flink.platform.udf.business;

import com.alibaba.fastjson.JSON;
import com.flink.platform.udf.entity.LabelWrapper;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.InputGroup;
import org.apache.flink.table.functions.ScalarFunction;

/**
 * @Author Shik
 * @Title: HbaseCellJsonFunction
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/28 下午5:12
 */
public class HbaseCellJsonFunction extends ScalarFunction {

    public String eval(Long signId, String desc,
                       @DataTypeHint(inputGroup = InputGroup.ANY) Object value,
                       Double weight, Long validTime) {
        LabelWrapper labelWrapper = new LabelWrapper();
        labelWrapper.setSign_id(signId);
        labelWrapper.setDesc(desc);
        labelWrapper.setValue(value);
        labelWrapper.setWeight(weight);
        labelWrapper.setValid_time(validTime);
        return JSON.toJSONString(labelWrapper);
    }

}
