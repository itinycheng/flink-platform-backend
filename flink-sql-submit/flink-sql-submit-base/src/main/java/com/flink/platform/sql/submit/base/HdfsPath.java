package com.flink.platform.sql.submit.base;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.flink.platform.common.constants.Constant.LINE_SEPARATOR;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    @Override
    public String readAndDelete() throws IOException {
        StringBuilder builder = new StringBuilder();
        try (FileSystem fs = createFileSystem();
                BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(filePath), UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(LINE_SEPARATOR);
            }

            fs.delete(filePath, false);
        }

        return builder.toString();
    }

    private FileSystem createFileSystem() throws IOException {
        return FileSystem.get(new Configuration());
    }
}
