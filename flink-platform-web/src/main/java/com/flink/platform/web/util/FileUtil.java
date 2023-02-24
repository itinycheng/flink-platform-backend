package com.flink.platform.web.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

/** File utils. */
public class FileUtil {

    public static void writeToFile(Path file, String data) throws IOException {
        if (Files.exists(file)) {
            Files.delete(file);
        }

        Files.write(file, data.getBytes(StandardCharsets.UTF_8));
    }

    public static void setPermissions(String filePath, String permissions) throws IOException {
        Files.setPosixFilePermissions(
                Paths.get(filePath), PosixFilePermissions.fromString(permissions));
    }
}
