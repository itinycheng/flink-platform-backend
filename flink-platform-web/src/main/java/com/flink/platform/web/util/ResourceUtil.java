package com.flink.platform.web.util;

import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.ResourceManageService;
import com.flink.platform.web.service.StorageService;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import static com.flink.platform.common.constants.Constant.DOT;
import static com.flink.platform.common.constants.Constant.SLASH;
import static com.flink.platform.common.constants.Constant.TMP;

/** Resource util. */
public class ResourceUtil {

    public static final String RESOURCE_DIR = "resource";

    /**
     * TODO: Two options to replace this user_id_ prefix with workspace semantics:
     *
     * <p>Option A — Simple prefix swap: Replace user_id_ with workspace_id_. Requires renaming all
     * existing files on storage and updating full_name in t_resource accordingly. One-time migration
     * cost; do NOT rename in-place.
     *
     * <p>Option B — Decouple prefix from full_name: Strip the root prefix out of full_name so it
     * only stores the user-defined directory structure (e.g. "/folder/sub/file.jar"). The actual
     * storage path is assembled at runtime: "{workspace_id_<id>}/resource/{full_name}". This way
     * full_name never needs to be migrated again if the ownership model changes in the future;
     * only a single workspace-level directory rename on storage is needed.
     */
    public static final String USER_DIR_PREFIX = "user_id_";

    private static final String localRootPath = PathUtil.getLocalWorkRootPath();

    public static String getAbsoluteStoragePath(String pathOrId) {
        try {
            if (NumberUtils.isParsable(pathOrId)) {
                var id = NumberUtils.toLong(pathOrId);
                var service = SpringContext.getBean(ResourceService.class);
                var resource = service.getById(id);
                pathOrId = resource.getFullName();
            }

            var service = SpringContext.getBean(StorageService.class);
            if (!service.isAbsolutePath(pathOrId)) {
                throw new IllegalArgumentException("The path is not absolute: " + pathOrId);
            }

            return service.normalizePath(pathOrId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String randomLocalTmpFile() {
        var fileName = String.join(DOT, UUID.randomUUID().toString(), TMP);
        return String.join(SLASH, localRootPath, fileName);
    }

    public static void copyToLocal(MultipartFile file, String filePath) throws IOException {
        var dstFile = new File(filePath);
        var parentDir = new File(dstFile.getParent());

        if (!parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }

        Files.copy(file.getInputStream(), dstFile.toPath());
    }

    public static String copyFromStorageToLocal(String storagePath) throws IOException {
        var service = SpringContext.getBean(ResourceManageService.class);
        return service.copyFromStorageToLocal(storagePath);
    }
}
