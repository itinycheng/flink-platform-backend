package com.flink.platform.web.service;

import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.util.PathUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.flink.platform.common.constants.Constant.EMPTY;
import static com.flink.platform.common.constants.Constant.OS_FILE_SEPARATOR;
import static com.flink.platform.web.util.ResourceUtil.RESOURCE_DIR;
import static com.flink.platform.web.util.ResourceUtil.USER_DIR_PREFIX;

/** Resource manage service. */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ResourceManageService {

    private final ResourceService resourceService;

    private final StorageService storageService;

    public boolean save(Resource entity) throws Exception {
        switch (entity.getType()) {
            case DIR:
                String parentPath = null;
                if (entity.getPid() != null) {
                    var parentResource = resourceService.getById(entity.getPid());
                    if (!parentResource.getType().isDir()) {
                        throw new RuntimeException("parent not a directory");
                    }
                    parentPath = parentResource.getFullName();
                }
                String absolutePath = getAbsStorageFilePath(entity.getUserId(), parentPath, entity.getName());
                if (!storageService.exists(absolutePath)) {
                    storageService.mkDir(absolutePath);
                    entity.setFullName(absolutePath);
                    return resourceService.save(entity);
                }
                break;
            case JAR:
            case SHELL:
                if (storageService.exists(entity.getFullName())) {
                    return resourceService.save(entity);
                }
                break;
            default:
                throw new RuntimeException("Unsupported resource type");
        }

        throw new RuntimeException("illegal state");
    }

    public boolean removeById(Long resourceId) throws Exception {
        Resource resource = resourceService.getById(resourceId);
        if (resource == null) {
            return false;
        }

        String fullName = resource.getFullName();
        if (storageService.exists(fullName)) {
            storageService.trashOrDelete(fullName, false);
        }
        return resourceService.removeById(resource.getId());
    }

    public String getAbsStorageFilePath(Long userId, String parentPath, String fileName) {
        var fileSeparator = storageService.getFileSeparator();
        if (StringUtils.isBlank(parentPath)) {
            var userDir = USER_DIR_PREFIX + userId;
            var storageRootPath = storageService.getRootPath();
            parentPath = String.join(fileSeparator, storageRootPath, RESOURCE_DIR, userDir);
        }
        return String.join(fileSeparator, parentPath, fileName);
    }

    public String copyFromStorageToLocal(String storagePath) throws IOException {
        var localRootPath = PathUtil.getLocalWorkRootPath();
        var rootPath = storageService.getRootPath();
        storagePath = storageService.normalizePath(storagePath);
        if (!storagePath.startsWith(rootPath)) {
            throw new IllegalArgumentException(
                    String.format("storage path %s not in root path %s", storagePath, rootPath));
        }

        var storageSeparator = storageService.getFileSeparator();
        var relativePath = storagePath.replaceFirst(rootPath, EMPTY);
        if (relativePath.startsWith(storageSeparator)) {
            relativePath = relativePath.substring(storageSeparator.length());
        }

        if (!OS_FILE_SEPARATOR.equals(storageSeparator)) {
            relativePath = relativePath.replace(storageSeparator, OS_FILE_SEPARATOR);
        }

        var localPath = String.join(OS_FILE_SEPARATOR, localRootPath, relativePath);
        storageService.copyFileToLocalIfChanged(storagePath, localPath);
        return localPath;
    }
}
