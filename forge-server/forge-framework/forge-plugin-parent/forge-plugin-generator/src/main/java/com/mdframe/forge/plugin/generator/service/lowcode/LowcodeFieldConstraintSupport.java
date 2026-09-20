package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 低代码字段存储容量约束。数据库物理范围与设计器自定义范围在这里统一计算。
 */
public final class LowcodeFieldConstraintSupport {

    private static final BigDecimal TINYINT_MIN = BigDecimal.valueOf(Byte.MIN_VALUE);
    private static final BigDecimal TINYINT_MAX = BigDecimal.valueOf(Byte.MAX_VALUE);
    private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal BIGINT_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal BIGINT_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal JAVASCRIPT_SAFE_INTEGER = BigDecimal.valueOf(9_007_199_254_740_991L);
    private static final Set<String> INTEGRAL_TYPES = Set.of("tinyint", "int", "integer", "bigint");
    private static final Set<String> TEXT_COMPONENTS = Set.of("input", "textarea", "barcodeScanner");

    private LowcodeFieldConstraintSupport() {
    }

    public static String dataType(LowcodeFieldSchema field) {
        return StringUtils.defaultIfBlank(field == null ? null : field.getDataType(), "varchar")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public static boolean isNumeric(LowcodeFieldSchema field) {
        String type = dataType(field);
        return INTEGRAL_TYPES.contains(type) || "decimal".equals(type);
    }

    public static boolean isIntegral(LowcodeFieldSchema field) {
        return INTEGRAL_TYPES.contains(dataType(field));
    }

    public static int decimalTotalDigits(LowcodeFieldSchema field) {
        return field != null && field.getLength() != null ? field.getLength() : 18;
    }

    public static int decimalScale(LowcodeFieldSchema field) {
        return field != null && field.getPrecision() != null ? field.getPrecision() : 2;
    }

    public static int displayScale(LowcodeFieldSchema field) {
        if (isMinorUnitMoney(field)) {
            return field.getPrecision() == null ? 2 : field.getPrecision();
        }
        return "decimal".equals(dataType(field)) ? decimalScale(field) : 0;
    }

    public static BigDecimal databaseMinimum(LowcodeFieldSchema field) {
        BigDecimal minimum = switch (dataType(field)) {
            case "tinyint" -> TINYINT_MIN;
            case "int", "integer" -> INT_MIN;
            case "bigint" -> BIGINT_MIN;
            case "decimal" -> decimalMaximum(field).negate();
            default -> null;
        };
        return toDisplayUnit(field, minimum);
    }

    public static BigDecimal databaseMaximum(LowcodeFieldSchema field) {
        BigDecimal maximum = switch (dataType(field)) {
            case "tinyint" -> TINYINT_MAX;
            case "int", "integer" -> INT_MAX;
            case "bigint" -> BIGINT_MAX;
            case "decimal" -> decimalMaximum(field);
            default -> null;
        };
        return toDisplayUnit(field, maximum);
    }

    public static BigDecimal configuredMinimum(LowcodeFieldSchema field) {
        return decimalProp(field, "min", "minimum");
    }

    public static BigDecimal configuredMaximum(LowcodeFieldSchema field) {
        return decimalProp(field, "max", "maximum");
    }

    public static BigDecimal effectiveMinimum(LowcodeFieldSchema field) {
        return greater(databaseMinimum(field), configuredMinimum(field));
    }

    public static BigDecimal effectiveMaximum(LowcodeFieldSchema field) {
        return smaller(databaseMaximum(field), configuredMaximum(field));
    }

    /**
     * 把字段存储约束应用到运行组件。页面级配置可以继续收紧范围，不能放宽数据库范围。
     */
    public static void applyRuntimeConstraints(LowcodeFieldSchema field,
                                               String componentType,
                                               Map<String, Object> props) {
        if (field == null || props == null) {
            return;
        }
        if (field.getLength() != null && field.getLength() > 0 && TEXT_COMPONENTS.contains(componentType)) {
            Integer configured = positiveInteger(firstPresent(props.get("maxlength"), props.get("maxLength")));
            props.put("maxlength", configured == null ? field.getLength() : Math.min(field.getLength(), configured));
            props.remove("maxLength");
        }
        if (!Set.of("number", "inputNumber", "money").contains(componentType) || !isNumeric(field)) {
            return;
        }

        int storageScale = displayScale(field);
        Integer configuredPrecision = nonNegativeInteger(props.get("precision"));
        props.put("precision", configuredPrecision == null
                ? storageScale
                : Math.min(storageScale, configuredPrecision));

        BigDecimal minimum = greater(effectiveMinimum(field), firstDecimal(props.get("min"), props.get("minimum")));
        BigDecimal maximum = smaller(effectiveMaximum(field), firstDecimal(props.get("max"), props.get("maximum")));
        props.remove("minimum");
        props.remove("maximum");
        if (isJavascriptSafe(minimum)) {
            props.put("min", minimum);
        } else {
            props.remove("min");
        }
        if (isJavascriptSafe(maximum)) {
            props.put("max", maximum);
        } else {
            props.remove("max");
        }
    }

    public static BigDecimal decimalValue(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isMinorUnitMoney(LowcodeFieldSchema field) {
        return field != null
                && "MONEY".equalsIgnoreCase(StringUtils.trimToEmpty(field.getBusinessFieldType()))
                && isIntegral(field);
    }

    private static BigDecimal decimalMaximum(LowcodeFieldSchema field) {
        int totalDigits = decimalTotalDigits(field);
        int scale = decimalScale(field);
        if (totalDigits < 1 || scale < 0 || scale >= totalDigits) {
            return null;
        }
        return BigDecimal.TEN.pow(totalDigits - scale).subtract(BigDecimal.ONE.movePointLeft(scale));
    }

    private static BigDecimal toDisplayUnit(LowcodeFieldSchema field, BigDecimal value) {
        if (value == null || !isMinorUnitMoney(field)) {
            return value;
        }
        return value.movePointLeft(displayScale(field));
    }

    private static BigDecimal decimalProp(LowcodeFieldSchema field, String... keys) {
        if (field == null || field.getBasicProps() == null) {
            return null;
        }
        for (String key : keys) {
            BigDecimal value = decimalValue(field.getBasicProps().get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal firstDecimal(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            BigDecimal decimal = decimalValue(value);
            if (decimal != null) {
                return decimal;
            }
        }
        return null;
    }

    private static Object firstPresent(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer positiveInteger(Object value) {
        Integer result = nonNegativeInteger(value);
        return result != null && result > 0 ? result : null;
    }

    private static Integer nonNegativeInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int result = new BigDecimal(String.valueOf(value)).intValueExact();
            return result >= 0 ? result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal greater(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.max(right);
    }

    private static BigDecimal smaller(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.min(right);
    }

    private static boolean isJavascriptSafe(BigDecimal value) {
        return value != null && value.abs().compareTo(JAVASCRIPT_SAFE_INTEGER) <= 0;
    }
}
