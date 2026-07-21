package com.flink.platform.web.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.flink.platform.dao.entity.TagInfo;
import com.flink.platform.web.common.RequestContext;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTenantLineHandlerTest {

    private final WorkspaceTenantLineHandler handler = new WorkspaceTenantLineHandler();

    @BeforeAll
    static void initTableInfo() {
        // Register TagInfo's TableInfo so TableInfoHelper.getTableInfo(TagInfo.class) resolves the
        // table name outside a running MyBatis context. If TagInfo's @TableName ever changes, the
        // resolved name changes with it and the literal-"t_tag" assertions below start failing.
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TagInfo.class);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void enforcesTagTable_whenWorkspacePresent() {
        RequestContext.set(new RequestContext.Context(1L, 42L));
        assertFalse(handler.ignoreTable("t_tag"));
        assertFalse(handler.ignoreTable("T_TAG"));
        assertEquals(42L, ((LongValue) handler.getTenantId()).getValue());
        assertEquals("workspace_id", handler.getTenantIdColumn());
    }

    @Test
    void ignoresOtherTables_evenWithWorkspace() {
        RequestContext.set(new RequestContext.Context(1L, 42L));
        assertTrue(handler.ignoreTable("t_job_flow"));
        assertTrue(handler.ignoreTable("t_alert"));
    }

    @Test
    void ignoresTagTable_whenNoWorkspaceContext() {
        // e.g. Quartz / gRPC background threads with no request context
        assertTrue(handler.ignoreTable("t_tag"));
    }

    // --- End-to-end: run the real MyBatis-Plus interceptor and inspect the rewritten SQL ---

    private String rewrite(String sql) throws Exception {
        var interceptor = new TenantLineInnerInterceptor(handler);
        Method parserSingle = com.baomidou.mybatisplus.extension.parser.JsqlParserSupport.class.getDeclaredMethod(
                "parserSingle", String.class, Object.class);
        parserSingle.setAccessible(true);
        return (String) parserSingle.invoke(interceptor, sql, null);
    }

    @Test
    void rewritesTagSelect_withWorkspaceCondition() throws Exception {
        RequestContext.set(new RequestContext.Context(1L, 42L));
        var out = rewrite("SELECT id, code FROM t_tag WHERE id = 1");
        assertTrue(out.contains("workspace_id = 42"), out);
    }

    @Test
    void doesNotRewriteOtherTables() throws Exception {
        RequestContext.set(new RequestContext.Context(1L, 42L));
        var out = rewrite("SELECT id FROM t_job_flow WHERE id = 1");
        assertFalse(out.contains("workspace_id"), out);
    }

    @Test
    void doesNotRewriteTag_whenNoWorkspaceContext() throws Exception {
        var out = rewrite("SELECT id FROM t_tag WHERE id = 1");
        assertFalse(out.contains("workspace_id"), out);
    }
}
