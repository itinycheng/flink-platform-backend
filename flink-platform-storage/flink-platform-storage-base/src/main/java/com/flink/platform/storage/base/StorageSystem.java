package com.flink.platform.storage.base;

import java.io.Closeable;
import java.io.IOException;

/**
 * storage system.
 */
public interface StorageSystem extends Closeable {

    void open() throws IOException;

    StorageStatus getFileStatus(String filePath) throws IOException;

    void copyToLocalFile(String srcFile, String dstFile) throws IOException;

    void copyToLocalFileIfChanged(String hdfsFile, String localFile) throws IOException;

    void copyFromLocalFile(String srcFile, String dstFile, boolean delSrc, boolean overwrite) throws IOException;

    void createFile(String filePath, String data, boolean overwrite) throws IOException;

    boolean delete(String filePath, boolean recursive) throws IOException;

    boolean mkdir(String path) throws IOException;

    boolean exists(String path) throws IOException;

    boolean rename(String src, String dst) throws IOException;

    String normalizePath(String path) throws IOException;

    boolean isAbsolutePath(String path);
}
