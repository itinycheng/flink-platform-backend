package com.flink.platform.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
}
