package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.SLASH;

/** Resource util. */
public class ResourceUtil {

    private static final String RESOURCE_ROOT = "resource";

    private static final String localDataRootPath =
            SpringContext.getBean("localDataDir", String.class);

    private static final String hdfsRootPath =
            SpringContext.getBean("projectHdfsPath", String.class);

    public static String getHdfsUserDir(Long userId) {
        String userDir = "user_id_" + userId;
        return String.join(SLASH, hdfsRootPath, RESOURCE_ROOT, userDir);
    }

    public static String getHdfsRelativePath(String path, Long userId) {
        String hdfsUserDir = getHdfsUserDir(userId);
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
        String fileName = UUID.randomUUID().toString();
        return String.join(SLASH, localDataRootPath, fileName);
    }

    public static String getFullHdfsFilePath(Long userId, String parentPath, String fileName) {
        if (StringUtils.isBlank(parentPath)) {
            parentPath = getHdfsUserDir(userId);
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
}
