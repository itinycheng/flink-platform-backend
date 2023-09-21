package com.flink.platform.udf.business;

import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.functions.TableFunction;

import com.flink.platform.common.util.Preconditions;
import com.flink.platform.udf.common.FunctionName;
import com.flink.platform.udf.common.SqlColumn;
import com.flink.platform.udf.util.ClassUtil;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.stream.Collectors;

/** abstract table function. */
public class AbstractTableFunction<I, O> extends TableFunction<O> {

    public final Class<I> tableClass;

    public final List<SqlColumn> tableColumns;

    public final String functionName;

    @SuppressWarnings("unchecked")
    public AbstractTableFunction() {
        super();
        // validate columns' definition
        Class<I> tableClass =
                (Class<I>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        List<SqlColumn> sqlColumns = ClassUtil.extractSqlColumnAnnotation(tableClass);
        String tableDescription =
                this.getClass().getAnnotation(FunctionHint.class).output().value();
        String expectDescription = sqlColumns.stream()
                .map(sqlColumn -> String.join(" ", sqlColumn.name(), sqlColumn.type().sqlType))
                .collect(Collectors.joining(", ", "ROW<", ">"));
        Preconditions.checkThrow(
                !tableDescription.equalsIgnoreCase(expectDescription),
                () -> new RuntimeException("value of @DataTypeHint isn't correct"));

        this.functionName = this.getClass().getAnnotation(FunctionName.class).value();
        this.tableClass = tableClass;
        this.tableColumns = sqlColumns;
    }
}
