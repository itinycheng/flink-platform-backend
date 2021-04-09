package com.itiger.persona.util;

import java.io.InputStreamReader;
import java.util.Properties;

/**
 * @Author Shik
 * @Title: PropertyUtils
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2020/11/19 下午3:40
 */
public class PropertyUtils {

    public static final Properties props = new Properties();

    static {
        try {
            props.load(new InputStreamReader(
                    PropertyUtils.class.getClassLoader().getResourceAsStream("application.yml"),
                    "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
