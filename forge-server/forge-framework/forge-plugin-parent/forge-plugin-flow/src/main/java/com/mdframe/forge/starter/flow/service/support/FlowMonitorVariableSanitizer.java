package com.mdframe.forge.starter.flow.service.support;

import java.lang.reflect.Array;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed projection for variables exposed by the flow-monitor API.
 * Business form payloads and unknown variables stay server-side; only the
 * operational fields required by monitoring screens may cross the boundary.
 */
public final class FlowMonitorVariableSanitizer {

    private static final int MAX_COLLECTION_VALUES = 100;
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "action", "approvalresult", "approved", "businesskey", "businesstype",
            "deptid", "formkey", "initiator", "initiatorname", "modelkey",
            "objectcode", "outcome", "priority", "processdefinitionkey", "recordid",
            "result", "startuserid", "status", "taskdefinitionkey", "tenantid");

    private FlowMonitorVariableSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (variables == null || variables.isEmpty()) {
            return result;
        }
        variables.forEach((key, value) -> {
            if (key == null || !ALLOWED_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                return;
            }
            Object safeValue = safeValue(value);
            if (safeValue != null) {
                result.put(key, safeValue);
            }
        });
        return result;
    }

    private static Object safeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return value;
        }
        if (value instanceof Collection<?> collection) {
            return safeCollection(collection);
        }
        if (value.getClass().isArray()) {
            int length = Math.min(Array.getLength(value), MAX_COLLECTION_VALUES);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object item = safeScalar(Array.get(value, i));
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        }
        return null;
    }

    private static List<Object> safeCollection(Collection<?> collection) {
        List<Object> result = new ArrayList<>();
        for (Object value : collection) {
            if (result.size() >= MAX_COLLECTION_VALUES) {
                break;
            }
            Object item = safeScalar(value);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private static Object safeScalar(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || value instanceof TemporalAccessor) {
            return value;
        }
        return null;
    }
}
