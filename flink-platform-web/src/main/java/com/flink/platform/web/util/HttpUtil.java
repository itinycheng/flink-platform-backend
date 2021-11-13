package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static com.flink.platform.common.constants.Constant.COLON;

/** Http utils. */
@Slf4j
public class HttpUtil {

    private static final String LOCALHOST_URL = "http://127.0.0.1";

    public static String getUrlOrDefault(String routeUrl) {
        if (StringUtils.isBlank(routeUrl)) {
            routeUrl = String.join(COLON, LOCALHOST_URL, SpringContext.getServerPort());
        }
        return routeUrl.endsWith("/") ? routeUrl.substring(0, routeUrl.lastIndexOf("/")) : routeUrl;
    }
}
