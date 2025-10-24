package com.flink.platform.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/** File utils. */
public class FileUtil {

    public static void rewriteFile(Path file, String data) throws IOException {
        Files.createDirectories(file.getParent());
        // TODO：this code is not atomic, consider using a temp file and then rename it.
        Files.write(file, data.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING);
    }

    public static void setPermissions(Path file, String permissions) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }
}
