package com.mdframe.forge.plugin.generator.service.businessapp;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Request-scoped task-form timing details shared by nested resolvers. */
final class BusinessFlowTaskFormProfiler {

    private final ThreadLocal<Map<String, Long>> stages = new ThreadLocal<>();
    private final ThreadLocal<List<String>> notes = new ThreadLocal<>();

    void begin(Map<String, Long> requestStages) {
        stages.set(requestStages);
        notes.set(new ArrayList<>());
    }

    void end() {
        stages.remove();
        notes.remove();
    }

    void mark(String key, long startedAtNanos) {
        Map<String, Long> current = stages.get();
        if (current == null || StringUtils.isBlank(key)) {
            return;
        }
        current.merge(key, elapsedMillis(startedAtNanos), Long::sum);
    }

    void note(String note) {
        List<String> current = notes.get();
        if (current != null && StringUtils.isNotBlank(note)) {
            current.add(note);
        }
    }

    List<String> notes() {
        List<String> current = notes.get();
        return current == null || current.isEmpty() ? List.of() : List.copyOf(current);
    }

    static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
