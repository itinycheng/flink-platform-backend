package com.flink.platform.udf;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.catalog.DataTypeFactory;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.table.types.inference.TypeInference;

import com.flink.platform.common.enums.DataType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Optional;

/** Type convert utils. */
@Slf4j
public class TypeConvertFunction extends ScalarFunction {

    private static final String TRUE = "true";

    private static final String FALSE = "false";

    public Object eval(String boolOrNum, String type) {
        if (boolOrNum == null || "null".equalsIgnoreCase(boolOrNum)) {
            return null;
        }

        DataType dataType = DataType.of(type);
        switch (dataType) {
            case INT:
                Number intNum = toNumber(boolOrNum);
                return intNum != null ? intNum.intValue() : null;
            case LONG:
                Number longNum = toNumber(boolOrNum);
                return longNum != null ? longNum.longValue() : null;
            case FLOAT:
                Number floatNum = toNumber(boolOrNum);
                return floatNum != null ? floatNum.floatValue() : null;
            case DOUBLE:
                Number doubleNum = toNumber(boolOrNum);
                return doubleNum != null ? doubleNum.doubleValue() : null;
            case BOOL:
                return toBool(boolOrNum);
            default:
                throw new RuntimeException("unsupported type");
        }
    }

    private Number toNumber(String boolOrNum) {
        if (TRUE.equalsIgnoreCase(boolOrNum)) {
            return 1;
        } else if (FALSE.equalsIgnoreCase(boolOrNum)) {
            return 0;
        }

        if (NumberUtils.isCreatable(boolOrNum)) {
            return NumberUtils.createNumber(boolOrNum);
        } else {
            return null;
        }
    }

    private Boolean toBool(String boolOrNum) {
        try {
            return Boolean.parseBoolean(boolOrNum);
        } catch (Exception e) {
            log.error("Parse {} to boolean failed", boolOrNum, e);
            return null;
        }
    }

    @Override
    public TypeInference getTypeInference(DataTypeFactory typeFactory) {
        return TypeInference.newBuilder()
                .typedArguments(DataTypes.STRING(), DataTypes.STRING())
                .outputTypeStrategy(callContext -> {
                    if (!callContext.isArgumentLiteral(1) || callContext.isArgumentNull(1)) {
                        throw callContext.newValidationError("Literal expected for second argument.");
                    }
                    DataType dataType = callContext
                            .getArgumentValue(1, String.class)
                            .map(DataType::of)
                            .orElse(DataType.STRING);
                    switch (dataType) {
                        case INT:
                            return Optional.of(DataTypes.INT().nullable());
                        case LONG:
                            return Optional.of(DataTypes.BIGINT().nullable());
                        case FLOAT:
                            return Optional.of(DataTypes.FLOAT().nullable());
                        case DOUBLE:
                            return Optional.of(DataTypes.DOUBLE().nullable());
                        case BOOL:
                            return Optional.of(DataTypes.BOOLEAN().nullable());
                        case STRING:
                        default:
                            return Optional.of(DataTypes.STRING().nullable());
                    }
                })
                .build();
    }

    public static void main(String[] args) {
        System.out.println(new TypeConvertFunction().eval("true", "BOOL"));
        System.out.println(new TypeConvertFunction().eval("true", "LONG"));
        System.out.println(new TypeConvertFunction().eval("false", "LONG"));
        System.out.println(new TypeConvertFunction().eval("1.0", "LONG"));
        System.out.println(new TypeConvertFunction().eval("2.0", "DOUBLE"));
        System.out.println(new TypeConvertFunction().eval("3", "LONG"));
        System.out.println(new TypeConvertFunction().eval("null", "INT"));
        System.out.println(new TypeConvertFunction().eval("3", "STRING"));
    }
}
