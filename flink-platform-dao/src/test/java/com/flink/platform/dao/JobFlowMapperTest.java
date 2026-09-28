package com.flink.platform.dao;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.flink.platform.dao.mapper.JobFlowMapper;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobFlowMapperTest {

    @Test
    void dependencyLookupIgnoresWorkspaceTenantFilter() throws NoSuchMethodException {
        var method = JobFlowMapper.class.getMethod("selectExistingFlowIds", Collection.class);
        var annotation = method.getAnnotation(InterceptorIgnore.class);

        assertNotNull(annotation);
        assertEquals("true", annotation.tenantLine());
    }
}
