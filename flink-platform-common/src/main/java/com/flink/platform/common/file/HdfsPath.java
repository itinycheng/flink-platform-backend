package com.flink.platform.common.file;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class HdfsPath implements CommonPath {

    private final Path filePath;

    public HdfsPath(String filePath) {
        this.filePath = new Path(filePath);
    }

    @Override
    public String getName() {
        return filePath.getName();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        return new FilterInputStream(fs.open(filePath)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    fs.close();
                }
            }
        };
    }
}
