package com.flink.platform.web;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** read/write file test. */
public class FileTest {

    @Test
    public void test1() {
        System.out.println(UUID.randomUUID().toString());
        File file = new File("application.yml");
        System.out.println(file.getAbsolutePath());
    }

    @Test
    public void test2() throws IOException {
        Path path = Paths.get("tmp/t.log");
        FileUtils.write(path.toFile(), "data", StandardCharsets.UTF_8);
        System.out.println("~~~" + path.toAbsolutePath().toString());
    }
}
