package com.flink.platform.plugin.apollo.util;

import org.jasypt.intf.service.JasyptStatelessService;

/**
 * Jasypt utils.
 */
public class JasyptUtil {

    private static final String ENCRYPTED_VALUE_PREFIX = "ENC(";

    private static final String ENCRYPTED_VALUE_SUFFIX = ")";

    private static final JasyptStatelessService SERVICE = new JasyptStatelessService();

    /**
     * decrypt.
     */
    public static String decrypt(String wrappedInput, String password) {
        return SERVICE.decrypt(
                getEncryptedValue(wrappedInput),
                password,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * encrypt.
     */
    public static String encrypt(String input, String password) {
        String encrypt = SERVICE.encrypt(
                input, password, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        return ENCRYPTED_VALUE_PREFIX + encrypt + ENCRYPTED_VALUE_SUFFIX;
    }

    public static boolean isJasyptValue(final String value) {
        if (value == null) {
            return false;
        }

        final String trimmed = value.trim();
        return trimmed.startsWith(ENCRYPTED_VALUE_PREFIX) && trimmed.endsWith(ENCRYPTED_VALUE_SUFFIX);
    }

    private static String getEncryptedValue(final String value) {
        return value.substring(4, (value.length() - 1));
    }
}
