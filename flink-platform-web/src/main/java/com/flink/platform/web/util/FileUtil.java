package com.flink.platform.web.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/** File utils. */
public class FileUtil {

    public static void writeToFile(Path file, String data) throws IOException {
        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        if (!Files.exists(file)) {
            Files.createFile(file);
        }

        Files.write(file, data.getBytes(StandardCharsets.UTF_8), TRUNCATE_EXISTING);
    }

    public static void setPermissions(Path file, String permissions) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
    }
}
