package com.flink.platform.web.service;

import com.flink.platform.dao.entity.Resource;
import com.flink.platform.dao.service.ResourceService;
import com.flink.platform.web.util.ResourceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Resource manage service. */
@Service
public class ResourceManageService {

    @Autowired private ResourceService resourceService;

    @Autowired private HdfsService hdfsService;

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
                String hdfsAbsolutePath =
                        ResourceUtil.getFullHdfsFilePath(
                                entity.getUserId(), parentPath, entity.getName());
                if (!hdfsService.exists(hdfsAbsolutePath)) {
                    hdfsService.mkDirs(hdfsAbsolutePath);
                    entity.setFullName(hdfsAbsolutePath);
                    return resourceService.save(entity);
                }
                break;
            case JAR:
                if (hdfsService.exists(entity.getFullName())) {
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

        if (hdfsService.exists(resource.getFullName())) {
            hdfsService.delete(resource.getFullName(), false);
        }
        return resourceService.removeById(resource.getId());
    }
}
