package com.mdframe.forge.plugin.generator.domain.formula;

import java.util.*;

/**
 * Result of executing all formulas for a single business object instance.
 * <p>
 * Contains:
 * <ul>
 *   <li>Computed field values (field name → computed value)</li>
 *   <li>Per-field execution errors</li>
 *   <li>Overall success flag</li>
 *   <li>Execution metadata (elapsed time, formulas executed)</li>
 * </ul>
 */
public class ExecutionResult {
    private final boolean success;
    private final Map<String, Object> results;
    private final Map<String, List<String>> errors;
    private final List<String> executedFields;
    private final long elapsedMs;
    private final FormulaExecutionTrace trace;

    private ExecutionResult(Builder builder) {
        this.success = builder.success;
        this.results = Collections.unmodifiableMap(new LinkedHashMap<>(builder.results));
        this.errors = toUnmodifiable(builder.errors);
        this.executedFields = Collections.unmodifiableList(new ArrayList<>(builder.executedFields));
        this.elapsedMs = builder.elapsedMs;
        this.trace = builder.trace;
    }

    private static Map<String, List<String>> toUnmodifiable(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : source.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Whether ALL formulas executed without errors. */
    public boolean isSuccess() { return success; }

    /** Computed field values (formula field → computed value). */
    public Map<String, Object> getResults() { return results; }

    /** Get a single computed value by field name, or null if not computed. */
    public Object getResult(String fieldName) { return results.get(fieldName); }

    /** Per-field error messages. Key = field name, Value = error descriptions. */
    public Map<String, List<String>> getErrors() { return errors; }

    /** Check if a specific field had errors. */
    public boolean hasError(String fieldName) {
        return errors.containsKey(fieldName) && !errors.get(fieldName).isEmpty();
    }

    /** Fields that were successfully executed (in order). */
    public List<String> getExecutedFields() { return executedFields; }

    /** Execution wall-clock time in milliseconds. */
    public long getElapsedMs() { return elapsedMs; }

    /** Optional execution trace, null when trace collection is disabled. */
    public FormulaExecutionTrace getTrace() { return trace; }

    /** Whether this result contains execution trace details. */
    public boolean hasTrace() { return trace != null; }

    /** Number of formulas that executed successfully. */
    public int getSuccessCount() { return executedFields.size(); }

    /** Number of formulas that failed. */
    public int getErrorCount() { return errors.size(); }

    @Override
    public String toString() {
        return "ExecutionResult{success=" + success
            + ", executed=" + executedFields.size()
            + ", errors=" + errors.size()
            + ", elapsed=" + elapsedMs + "ms}";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        boolean success;
        Map<String, Object> results = new LinkedHashMap<>();
        Map<String, List<String>> errors = new LinkedHashMap<>();
        List<String> executedFields = new ArrayList<>();
        long elapsedMs;
        FormulaExecutionTrace trace;

        public Builder success(boolean s) { this.success = s; return this; }
        public Builder putResult(String field, Object value) {
            this.results.put(field, value);
            this.executedFields.add(field);
            return this;
        }
        public Builder putError(String field, String error) {
            this.errors.computeIfAbsent(field, k -> new ArrayList<>()).add(error);
            return this;
        }
        public Builder putErrors(String field, List<String> errorList) {
            this.errors.computeIfAbsent(field, k -> new ArrayList<>()).addAll(errorList);
            return this;
        }
        public Builder elapsedMs(long ms) { this.elapsedMs = ms; return this; }
        public Builder trace(FormulaExecutionTrace trace) { this.trace = trace; return this; }

        public ExecutionResult build() { return new ExecutionResult(this); }
    }
}
