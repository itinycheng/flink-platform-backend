package com.flink.platform.web;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/** read/write file test. */
class FileTest {

    @Test
    void test1() {
        System.out.println(UUID.randomUUID().toString());
        File file = new File("application.yml");
        System.out.println(file.getAbsolutePath());
    }

    @Test
    void test2() throws Exception {
        Path path = Path.of("tmp/t.log");
        FileUtils.write(path.toFile(), "data", StandardCharsets.UTF_8);
        System.out.println("~~~" + path.toAbsolutePath().toString());
    }
}
