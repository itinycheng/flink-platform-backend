package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.StorageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.JobConstant.TMP_FILE_SUFFIX;

/** Resource util. */
public class ResourceUtil {

    private static final String RESOURCE_DIR = "resource";

    private static final String localRootPath = SpringContext.getBean("localBasePath", String.class);

    private static final String hdfsRootPath = SpringContext.getBean("storageBasePath", String.class);

    private static final StorageService STORAGE_SERVICE = SpringContext.getBean(StorageService.class);

    public static String getStorageFilePath(String relativePath, Long userId) {
        String hdfsUserDir = getStorageUserDir(userId);
        return String.join(SLASH, hdfsUserDir, relativePath);
    }

    public static String getAbsoluteStoragePath(String path, Long userId) {
        if (STORAGE_SERVICE.isAbsolutePath(path)) {
            return path;
        } else {
            return getStorageFilePath(path, userId);
        }
    }

    public static String getHdfsRelativePath(String path, Long userId) {
        String hdfsUserDir = getStorageUserDir(userId);
        if (path.startsWith(hdfsUserDir)) {
            throw new RuntimeException("can not found");
        }
        String relativePath = path.substring(hdfsUserDir.length());
        if (relativePath.startsWith(SLASH)) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    public static String randomLocalTmpFile() {
        String fileName = String.join(DOT, UUID.randomUUID().toString(), TMP_FILE_SUFFIX);
        return String.join(SLASH, localRootPath, fileName);
    }

    /** Only for file upload. */
    public static String getFullStorageFilePath(Long userId, String parentPath, String fileName) {
        if (StringUtils.isBlank(parentPath)) {
            parentPath = getStorageUserDir(userId);
        }

        return String.join(SLASH, parentPath, fileName);
    }

    public static void copyToLocal(MultipartFile file, String fullFileName) throws IOException {
        File dstFile = new File(fullFileName);
        File parentDir = new File(dstFile.getParent());

        if (!parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }

        Files.copy(file.getInputStream(), dstFile.toPath());
    }

    public static String copyFromStorageToLocal(String hdfsPath) throws IOException {
        String localPath = hdfsPath.replace(hdfsRootPath, localRootPath);
        STORAGE_SERVICE.copyFileToLocalIfChanged(hdfsPath, localPath);
        return localPath;
    }

    private static String getStorageUserDir(Long userId) {
        String userDir = "user_id_" + userId;
        return String.join(SLASH, hdfsRootPath, RESOURCE_DIR, userDir);
    }

    private static String getLocalUserDir(Long userId) {
        String userDir = "user_id_" + userId;
        return String.join(SLASH, localRootPath, RESOURCE_DIR, userDir);
    }
}
