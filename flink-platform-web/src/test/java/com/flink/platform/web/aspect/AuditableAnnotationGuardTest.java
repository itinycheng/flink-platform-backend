package com.flink.platform.web.aspect;

import com.flink.platform.common.annotation.Auditable;
import com.flink.platform.common.enums.OperationType;
import com.flink.platform.dao.entity.Identifiable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard test: {@link com.flink.platform.web.aspect.AuditAspect} resolves the audited entity id by a
 * fixed precedence chain (return value → a single {@code Long} arg → a single {@code Identifiable}
 * request-body arg), with no per-method configuration. That only stays correct while every
 * {@code @Auditable} controller method has an <b>unambiguous</b> id source.
 *
 * <p>This test scans all {@code @RestController} classes for {@code @Auditable} methods and fails if
 * any method's shape could make the aspect pick the wrong id — e.g. two {@code Long} args (the
 * aspect takes the first), or no resolvable id source at all. If you add an audited method with an
 * ambiguous shape, this test goes red on purpose: give the method a single id source, or extend the
 * aspect's resolution logic (and this guard) deliberately.
 */
class AuditableAnnotationGuardTest {

    private static final String CONTROLLER_PACKAGE = "com.flink.platform.web.controller";

    @Test
    void everyAuditableMethodHasAnUnambiguousEntityIdSource() {
        var auditableMethods = findAuditableControllerMethods();
        assertFalse(
                auditableMethods.isEmpty(),
                "No @Auditable controller methods found — the scan is broken or the annotation moved");

        for (Method method : auditableMethods) {
            var operation = method.getAnnotation(Auditable.class).operation();
            long longArgCount = countArgs(method, AuditableAnnotationGuardTest::isLongType);
            long identifiableArgCount = countArgs(method, Identifiable.class::isAssignableFrom);
            boolean returnIsIdentifiable = returnDataIsIdentifiable(method);

            String where = method.getDeclaringClass().getSimpleName() + "#" + method.getName();

            // More than one candidate of either kind is a silent wrong-id hazard.
            assertTrue(
                    longArgCount <= 1,
                    where + " has " + longArgCount + " Long/long args; the audit id source is ambiguous");
            assertTrue(
                    identifiableArgCount <= 1,
                    where + " has " + identifiableArgCount + " Identifiable args; the audit id source is ambiguous");

            if (operation == OperationType.DELETE) {
                // DELETE re-reads BEFORE proceed(), so the return value is unavailable: the id must
                // come from the method args. AuditAspect.readIdFromArgs requires EXACTLY one arg
                // (it bails on args.length != 1), so the guard must assert the same to stay aligned —
                // a DELETE with an extra arg (e.g. a `reason`) would otherwise be silently unaudited.
                assertEquals(
                        1,
                        method.getParameterCount(),
                        where + " is DELETE but does not take exactly one arg; AuditAspect.readIdFromArgs "
                                + "bails on args.length != 1 and would silently skip the audit");
                assertTrue(
                        longArgCount == 1 || identifiableArgCount == 1,
                        where + " is DELETE but its single arg is neither a Long nor an Identifiable (no id source)");
            } else {
                // INSERT / UPDATE: return value first, else a single Long arg, else a body id.
                assertTrue(
                        returnIsIdentifiable || longArgCount == 1 || identifiableArgCount == 1,
                        where + " (" + operation + ") has no resolvable entity id source "
                                + "(return value is not Identifiable, and no single Long/Identifiable arg)");
            }
        }
    }

    private List<Method> findAuditableControllerMethods() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        var methods = new ArrayList<Method>();
        for (var candidate : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller;
            try {
                controller = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Cannot load scanned controller " + candidate.getBeanClassName(), e);
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Auditable.class)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private long countArgs(Method method, java.util.function.Predicate<Class<?>> predicate) {
        long count = 0;
        for (Class<?> paramType : method.getParameterTypes()) {
            if (predicate.test(paramType)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isLongType(Class<?> type) {
        return type == Long.class || type == long.class;
    }

    /** True when the method returns {@code ResultInfo<X>} with X assignable to {@link Identifiable}. */
    private boolean returnDataIsIdentifiable(Method method) {
        Type generic = method.getGenericReturnType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            return false;
        }
        Type[] typeArgs = parameterized.getActualTypeArguments();
        if (typeArgs.length != 1) {
            return false;
        }
        return typeArgs[0] instanceof Class<?> dataType && Identifiable.class.isAssignableFrom(dataType);
    }
}
