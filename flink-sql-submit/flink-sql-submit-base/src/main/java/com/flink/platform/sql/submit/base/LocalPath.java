package com.flink.platform.sql.submit.base;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LocalPath implements CommonPath {

    private final Path filePath;

    public LocalPath(String filePath) {
        this.filePath = Paths.get(filePath);
    }

    @Override
    public String getName() {
        return filePath.toFile().getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(filePath);
    }

    @Override
    public String readAndDelete() throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        Files.deleteIfExists(filePath);
        return new String(bytes, UTF_8);
    }
}
