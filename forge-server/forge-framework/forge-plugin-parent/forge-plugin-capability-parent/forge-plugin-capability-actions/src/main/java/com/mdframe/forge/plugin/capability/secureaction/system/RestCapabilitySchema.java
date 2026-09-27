package com.mdframe.forge.plugin.capability.secureaction.system;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mdframe.forge.starter.core.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.Set;

/** 从受控 DTO 导出字段白名单；不接受任意 Map/Object 或循环结构。 */
final class RestCapabilitySchema {
    private final ObjectMapper mapper;

    RestCapabilitySchema(ObjectMapper mapper) { this.mapper = mapper; }

    ObjectNode describe(JavaType type) { return describe(type, new LinkedHashSet<>(), 0); }

    private ObjectNode describe(JavaType type, Set<String> parents, int depth) {
        if (type == null || depth > 8 || type.isJavaLangObject() || type.isMapLikeType()) {
            throw new BusinessException("REST 参数必须使用明确 DTO，不能使用任意 Object/Map 或超过八层的结构");
        }
        ObjectNode schema = mapper.createObjectNode();
        Class<?> raw = type.getRawClass();
        if (raw == Boolean.class || raw == boolean.class) {
            return schema.put("type", "boolean");
        }
        if (raw == Long.class || raw == long.class || raw == java.math.BigInteger.class) {
            return schema.put("type", "string").put("description", "十进制整数字符串，避免长 ID 精度丢失");
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive() && raw != char.class) {
            return schema.put("type", raw == float.class || raw == double.class
                    || raw == Float.class || raw == Double.class || raw == java.math.BigDecimal.class ? "number" : "integer");
        }
        if (raw == String.class || raw == Character.class || raw == char.class
                || raw.getName().startsWith("java.time.")) {
            return schema.put("type", "string");
        }
        if (raw.isEnum()) {
            schema.put("type", "string");
            var values = schema.putArray("enum");
            for (Object value : raw.getEnumConstants()) { values.add(((Enum<?>) value).name()); }
            return schema;
        }
        if (type.isCollectionLikeType() || type.isArrayType()) {
            schema.put("type", "array").put("maxItems", 1000);
            schema.set("items", describe(type.getContentType(), parents, depth + 1));
            return schema;
        }
        if (!raw.getName().startsWith("com.mdframe.") || !parents.add(type.toCanonical())) {
            throw new BusinessException("REST 参数包含未支持或循环引用的类型");
        }
        schema.put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        for (var property : mapper.getDeserializationConfig().introspect(type).findProperties()) {
            if (property.couldDeserialize()) {
                properties.set(property.getName(), describe(property.getPrimaryType(),
                        new LinkedHashSet<>(parents), depth + 1));
            }
        }
        return schema;
    }
}
