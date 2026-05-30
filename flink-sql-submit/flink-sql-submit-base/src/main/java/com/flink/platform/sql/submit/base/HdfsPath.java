package com.flink.platform.sql.submit.base;

import com.flink.platform.common.util.IOUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

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
        FileSystem fs = FileSystem.get(new Configuration());
        return IOUtil.closingWith(fs.open(filePath), fs);
    }
}
