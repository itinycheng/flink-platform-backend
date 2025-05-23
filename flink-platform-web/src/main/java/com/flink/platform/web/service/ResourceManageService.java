package com.flink.platform.web.service;

import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.flink.platform.web.util.ResourceUtil.getFullStorageFilePath;

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
                    Resource parentResource = resourceService.getById(entity.getPid());
                    if (parentResource.getType().isFile()) {
                        throw new RuntimeException("parent not a directory");
                    }
                    parentPath = parentResource.getFullName();
                }
                String absolutePath = getFullStorageFilePath(entity.getUserId(), parentPath, entity.getName());
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
}
