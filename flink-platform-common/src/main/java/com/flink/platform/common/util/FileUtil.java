package com.flink.platform.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/** File utils. */
public class FileUtil {

    public static void rewriteFile(Path file, String data) throws IOException {
        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(file)) {
            Files.createFile(file);
        }

        Files.write(file, data.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING, SYNC);
    }

    public static void setPermissions(Path file, String permissions) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }
}
