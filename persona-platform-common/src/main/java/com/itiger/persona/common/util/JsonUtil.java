package com.itiger.persona.common.util;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

/**
 * @author tiger
 */
@Slf4j
public class JsonUtil {

    public static <T> T toJson(String res, Class<T> clazz) {
        try {
            return JSON.parseObject(res, clazz);
        } catch (Exception e) {
            log.error("parseObject is error");
            return null;
        }
    }

    public static String toJsonString(Object obj) {
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.error("parseObject is error");
            return null;
        }
    }

}
