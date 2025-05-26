package com.flink.platform.web.util;

import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.StorageService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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

    private static final String localRootPath = PathUtil.getWorkRootPath();

    private static final String storageRootPath = SpringContext.getBean("storageBasePath", String.class);

    private static final StorageService STORAGE_SERVICE = SpringContext.getBean(StorageService.class);

    private static final ResourceService RESOURCE_SERVICE = SpringContext.getBean(ResourceService.class);

    public static String getStorageFilePath(String relativePath, Long userId) {
        String storageUserDir = getStorageUserDir(userId);
        return String.join(SLASH, storageUserDir, relativePath);
    }

    public static String getAbsoluteStoragePath(String path, Long userId) {
        try {
            if (NumberUtils.isParsable(path)) {
                long id = NumberUtils.toLong(path);
                Resource resource = RESOURCE_SERVICE.getById(id);
                path = resource.getFullName();
            }

            if (STORAGE_SERVICE.isAbsolutePath(path)) {
                return STORAGE_SERVICE.normalizePath(path);
            } else {
                return getStorageFilePath(path, userId);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getRelativeStoragePath(String path, Long userId) {
        String storageUserDir = getStorageUserDir(userId);
        if (path.startsWith(storageUserDir)) {
            throw new RuntimeException("can not found");
        }
        String relativePath = path.substring(storageUserDir.length());
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

    public static String copyFromStorageToLocal(String storagePath) throws IOException {
        String localPath = storagePath.replace(storageRootPath, localRootPath);
        STORAGE_SERVICE.copyFileToLocalIfChanged(storagePath, localPath);
        return localPath;
    }

    private static String getStorageUserDir(Long userId) {
        String userDir = "user_id_" + userId;
        return String.join(SLASH, storageRootPath, RESOURCE_DIR, userDir);
    }
}
