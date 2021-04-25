package com.itiger.persona.web;

import org.apache.hadoop.fs.Path;
import org.junit.Test;

import java.io.File;

public class FileTest {

    @Test
    public void test1() {
        File file = new File("application.yml");
        System.out.println(file.getAbsolutePath());
    }

    @Test
    public void test2() {
        Path path = new Path("/Users/tiger/IdeaProjects/persona-platform-backend/persona-platform-web/application.yml");
        System.out.println(path.toUri().toString());
    }
}
