package com.flink.platform.web.config;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.flink.platform.common.annotation.TenantId;
import com.flink.platform.web.common.RequestContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.HashSet;
import java.util.Set;

import static java.util.Locale.ROOT;

/**
 * Tenant handler that scopes queries to the current request's workspace.
 *
 * <p>Scoped tables and the tenant column are discovered from entity fields annotated with
 * {@link TenantId}: any table whose entity has a {@code @TenantId} field is workspace-scoped. The
 * tenant column is fixed to {@code workspace_id}; each {@code @TenantId} field is validated at
 * discovery time to map to that column (MyBatis-Plus uses a single global tenant column). Discovery
 * runs once, lazily, and is cached — there is zero per-query reflection, and no separate validator.
 */
public class WorkspaceTenantLineHandler implements TenantLineHandler {

    private static final String TENANT_COLUMN = "workspace_id";

    private volatile boolean initialized;

    private Set<String> tenantTables;

    @Override
    public Expression getTenantId() {
        return new LongValue(RequestContext.requireWorkspaceId());
    }

    @Override
    public String getTenantIdColumn() {
        ensureInitialized();
        return TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (RequestContext.getWorkspaceId() == null) {
            return true;
        }

        ensureInitialized();
        return !tenantTables.contains(tableName.toLowerCase(ROOT));
    }

    /**
     * Scan entity metadata once for {@code @TenantId} fields. Lazy (not in the constructor) because
     * MyBatis-Plus builds {@code TableInfo} during mapper scanning at startup; by the time a
     * workspace-scoped query runs, every entity is registered.
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            var tables = new HashSet<String>();
            for (var tableInfo : TableInfoHelper.getTableInfos()) {
                for (var fieldInfo : tableInfo.getFieldList()) {
                    var field = fieldInfo.getField();
                    if (field == null || !field.isAnnotationPresent(TenantId.class)) {
                        continue;
                    }

                    if (!TENANT_COLUMN.equals(fieldInfo.getColumn().toLowerCase(ROOT))) {
                        throw new IllegalStateException(String.format(
                                "@TenantId on %s.%s maps to column '%s', but the tenant filter uses a single "
                                        + "global column '%s'; every @TenantId field must map to it.",
                                tableInfo.getEntityType().getName(),
                                fieldInfo.getProperty(),
                                fieldInfo.getColumn(),
                                TENANT_COLUMN));
                    }

                    tables.add(tableInfo.getTableName().toLowerCase(ROOT));
                }
            }

            tenantTables = Set.copyOf(tables);
            initialized = true;
        }
    }
}
