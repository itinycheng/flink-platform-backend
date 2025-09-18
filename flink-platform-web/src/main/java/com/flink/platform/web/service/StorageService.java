package com.flink.platform.web.service;

import com.flink.platform.storage.base.StorageStatus;
import com.flink.platform.storage.base.StorageSystem;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** service for uploading/downloading resources. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class StorageService {

    private final StorageSystem storageSystem;

    public boolean isDistributed() {
        return storageSystem.isDistributed();
    }

    public String getRootPath() {
        return storageSystem.getRootPath();
    }

    public StorageStatus getFileStatus(String filePath) throws IOException {
        return storageSystem.getFileStatus(filePath);
    }

    public void copyFileToLocalIfChanged(String hdfsFile, String localFile) throws IOException {
        storageSystem.copyToLocalFileIfChanged(hdfsFile, localFile);
    }

    public boolean delete(String dstPath, boolean recursive) throws IOException {
        return storageSystem.delete(dstPath, recursive);
    }

    public boolean trashOrDelete(String path, boolean recursive) throws IOException {
        if (storageSystem.moveToTrash(path)) {
            return true;
        }

        return storageSystem.delete(path, recursive);
    }

    public void copyFromLocal(String srcFile, String dstFile, boolean deleteSrc, boolean overwrite) throws IOException {
        storageSystem.copyFromLocalFile(srcFile, dstFile, deleteSrc, overwrite);
    }

    public void createFile(String filePath, String data, boolean overwrite) throws IOException {
        storageSystem.createFile(filePath, data, overwrite);
    }

    public boolean mkDir(String path) throws IOException {
        return storageSystem.mkdir(path);
    }

    public boolean exists(String path) throws IOException {
        if (StringUtils.isEmpty(path)) {
            return false;
        }
        return storageSystem.exists(path);
    }

    public String normalizePath(String path) throws IOException {
        if (StringUtils.isEmpty(path)) {
            return path;
        }

        return storageSystem.normalizePath(path);
    }

    public boolean isAbsolutePath(String path) {
        return storageSystem.isAbsolutePath(path);
    }

    @PreDestroy
    public void destroy() {
        try {
            storageSystem.close();
        } catch (Exception ignored) {
        }
    }
}
