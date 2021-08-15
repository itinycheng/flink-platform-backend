package com.itiger.persona.flink.udf.business;

import com.alibaba.fastjson.JSON;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.functions.ScalarFunction;

import java.util.List;

/**
 * @Author Shik
 * @Title: ListParseFunction
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/28 下午2:48
 */
public class ListParseFunction extends ScalarFunction {

    public List<String> eval(String value) {
        if (StringUtils.isNotBlank(value)) {
            List<String> list = JSON.parseObject(value, List.class);
            if(CollectionUtils.isNotEmpty(list)) {
                return list;
            }
        }
        return null;

    }

}
