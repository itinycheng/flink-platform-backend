package com.flink.platform.web.util;

import com.flink.platform.web.common.SpringContext;
import com.flink.platform.web.service.StorageService;

/**
 * Storage utils.
 */
public class StorageUtil {
    private static final StorageService storageService = SpringContext.getBean(StorageService.class);
}
