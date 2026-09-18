package com.mdframe.forge.plugin.print.dto;

import com.mdframe.forge.plugin.print.enums.PrintExecutionResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.util.Set;

public record PrintExecutionEventDTO(@NotNull PrintExecutionResult result, @Min(1) @Max(50) Integer pageCount, @Size(max = 64) String errorCode) {

    private static final Set<String> ERRORS = Set.of("PRINT_FAILED", "PRINT_CANCELLED", "RESOURCE_FAILED", "RESOURCE_TIMEOUT", "FIELD_NOT_ALLOWED", "INVALID_TEMPLATE", "FONT_UNAVAILABLE", "LIMIT_EXCEEDED", "ELEMENT_TOO_TALL", "PRINT_UNAVAILABLE");

    @JsonIgnore
    @AssertTrue
    public boolean isEventValid() {
        return result == PrintExecutionResult.DIALOG_OPENED ? pageCount != null && errorCode == null : result == PrintExecutionResult.FAILED && errorCode != null && ERRORS.contains(errorCode);
    }
}
