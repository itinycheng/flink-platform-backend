package com.flink.platform.udf.business;

import org.apache.commons.lang3.StringUtils;
import org.apache.flink.table.functions.ScalarFunction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Shik
 * @Title: BirthdayParseFunction
 * @ProjectName: flink-platform-backend
 * @Description: TODO
 * @Date: 2021/5/28 上午10:32
 */
public class BirthdayParseFunction extends ScalarFunction {

    public static final Pattern p = Pattern.compile("(^\\d{4}-\\d{1,2}-\\d{1,2})");

    public Integer eval(String date) {
        if(StringUtils.isNotBlank(date)) {
            Matcher m = p.matcher(date);
            if (m.find()) {
                date = m.group(1);
                String yyyyMMdd = date.replace("-", "");
                return Integer.parseInt(yyyyMMdd);
            }
        }
        return null;

    }

}
