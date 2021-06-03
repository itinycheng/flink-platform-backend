package com.itiger.persona.config.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author Shik
 * @Title: HeaderChecker
 * @ProjectName: datapipeline
 * @Description: TODO
 * @Date: 2021/3/3 下午5:39
 */
@Documented
@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TokenChecker {

    /**
     * Without default value means this argument is required
     *
     * @return Header names
     */
    String token() default "authorization";

}
