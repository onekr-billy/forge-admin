package com.mdframe.forge.plugin.external.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ExternalSensitiveDataMasker {

    public static final String MASK = "******";

    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "token", "password", "passwd", "secret", "authorization", "cookie",
            "apikey", "mobile", "phone", "telephone", "tel", "orderno", "paymentno",
            "tradeno", "cardno", "bankcard", "idcard", "identityno", "credential");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+\\-/=]+");
    private static final Pattern SENSITIVE_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)((?:token|password|passwd|secret|authorization|cookie|api[_-]?key|mobile|phone|telephone|tel|"
                    + "order[_-]?no|payment[_-]?no|trade[_-]?no|card[_-]?no|bank[_-]?card|id[_-]?card|"
                    + "identity[_-]?no|credential)\\s*[:=]\\s*)([^,;\\s&]+)");

    public String maskJson(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            Object parsed = JSON.parse(value);
            maskValue(parsed);
            return JSON.toJSONString(parsed);
        } catch (Exception ignored) {
            return maskText(value);
        }
    }

    public String maskUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            StringBuilder safe = new StringBuilder();
            if (uri.getScheme() != null) {
                safe.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                safe.append(uri.getHost());
            }
            if (uri.getPort() >= 0) {
                safe.append(':').append(uri.getPort());
            }
            if (uri.getRawPath() != null) {
                safe.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                safe.append('?');
                String[] pairs = uri.getRawQuery().split("&");
                for (int index = 0; index < pairs.length; index++) {
                    if (index > 0) {
                        safe.append('&');
                    }
                    int separator = pairs[index].indexOf('=');
                    safe.append(separator < 0 ? pairs[index] : pairs[index].substring(0, separator));
                    safe.append('=').append(MASK);
                }
            }
            return safe.toString();
        } catch (Exception ignored) {
            int queryIndex = value.indexOf('?');
            return maskText(queryIndex < 0 ? value : value.substring(0, queryIndex) + "?" + MASK);
        }
    }

    public String maskText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = MOBILE_PATTERN.matcher(value).replaceAll("1**********");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("$1" + MASK);
        return SENSITIVE_ASSIGNMENT_PATTERN.matcher(masked).replaceAll("$1" + MASK);
    }

    @SuppressWarnings("unchecked")
    private void maskValue(Object value) {
        if (value instanceof JSONObject object) {
            maskMap(object);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> mutableMap = (Map<Object, Object>) map;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object child = entry.getValue();
                if (isSensitiveKey(String.valueOf(entry.getKey()))) {
                    mutableMap.put(entry.getKey(), MASK);
                } else if (child instanceof String text) {
                    mutableMap.put(entry.getKey(), maskText(text));
                } else {
                    maskValue(child);
                }
            }
            return;
        }
        if (value instanceof JSONArray array) {
            for (int index = 0; index < array.size(); index++) {
                Object child = array.get(index);
                if (child instanceof String text) {
                    array.set(index, maskText(text));
                } else {
                    maskValue(child);
                }
            }
            return;
        }
        if (value instanceof java.util.List<?> list) {
            java.util.List<Object> mutableList = (java.util.List<Object>) list;
            for (int index = 0; index < mutableList.size(); index++) {
                Object child = mutableList.get(index);
                if (child instanceof String text) {
                    mutableList.set(index, maskText(text));
                } else {
                    maskValue(child);
                }
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(this::maskValue);
        }
    }

    private void maskMap(JSONObject object) {
        for (String key : object.keySet()) {
            Object child = object.get(key);
            if (isSensitiveKey(key)) {
                object.put(key, MASK);
            } else {
                maskValue(child);
                if (child instanceof String text) {
                    object.put(key, maskText(text));
                }
            }
        }
    }

    boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }
}
