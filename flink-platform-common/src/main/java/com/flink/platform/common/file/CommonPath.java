package com.flink.platform.common.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * path handler, handle different file system path.
 */
public interface CommonPath {

    String getName();

    InputStream getInputStream() throws IOException;

    static CommonPath parse(String path) {
        if (path.startsWith("hdfs:")) {
            return new HdfsPath(path);
        } else {
            return new LocalPath(path);
        }
    }
}
