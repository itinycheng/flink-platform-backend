package com.itiger.persona.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * utils
 *
 * @author tiny.wang
 */
public class Utils {

    public static final Pattern APP_ID_PATTERN = Pattern.compile("yarn\\s+application\\s+-kill\\s+(\\S+)");

    public static final Pattern JOB_ID_PATTERN = Pattern.compile("Job\\s+has\\s+been\\s+submitted\\s+with\\s+JobID\\s+(\\S+)");

    public static String extractApplicationId(String message) {
        Matcher matcher = APP_ID_PATTERN.matcher(message);
        if (matcher.matches()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    public static String extractJobId(String message) {
        Matcher matcher = JOB_ID_PATTERN.matcher(message);
        if (matcher.matches()) {
            return matcher.group();
        } else {
            return null;
        }
    }

}
