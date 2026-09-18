package com.mdframe.forge.plugin.print.dto;

import com.mdframe.forge.plugin.print.enums.*;
import com.mdframe.forge.plugin.print.spi.PrintSourceRequest;
import jakarta.validation.constraints.*;

public record PrintBindingQueryDTO(@NotNull @Positive Long applicationId, @NotNull PrintSourceType sourceType, @Positive Long pageId, @Size(max = 128) String formKey, @NotBlank String objectCode, @NotNull PrintScene scene) {

    public PrintSourceRequest source() {
        return new PrintSourceRequest(applicationId, sourceType, pageId, formKey, objectCode);
    }
}
