package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

import static com.flink.platform.common.constants.Constant.COLON;
import static com.flink.platform.common.constants.Constant.COMMA;

/** Http utils. */
@Slf4j
public class HttpUtil {

    public static final String HTTP_HEADER_UNKNOWN = "unKnown";

    public static final String HTTP_X_FORWARDED_FOR = "X-Forwarded-For";

    public static final String HTTP_X_REAL_IP = "X-Real-IP";

    public static final String LOCALHOST_URL = "http://127.0.0.1";

    public static String getUrlOrDefault(String routeUrl) {
        if (StringUtils.isBlank(routeUrl)) {
            routeUrl = String.join(COLON, LOCALHOST_URL, SpringContext.getServerPort());
        }
        return routeUrl.endsWith("/") ? routeUrl.substring(0, routeUrl.lastIndexOf("/")) : routeUrl;
    }

    public static String getClientIpAddress(HttpServletRequest request) {
        String clientIp = request.getHeader(HTTP_X_FORWARDED_FOR);
        if (StringUtils.isNotEmpty(clientIp) && !clientIp.equalsIgnoreCase(HTTP_HEADER_UNKNOWN)) {
            int index = clientIp.indexOf(COMMA);
            if (index != -1) {
                return clientIp.substring(0, index);
            } else {
                return clientIp;
            }
        }

        clientIp = request.getHeader(HTTP_X_REAL_IP);
        if (StringUtils.isNotEmpty(clientIp) && !clientIp.equalsIgnoreCase(HTTP_HEADER_UNKNOWN)) {
            return clientIp;
        }

        return request.getRemoteAddr();
    }
}
