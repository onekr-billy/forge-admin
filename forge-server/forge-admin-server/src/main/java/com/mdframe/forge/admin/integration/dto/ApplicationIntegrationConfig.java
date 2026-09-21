package com.mdframe.forge.admin.integration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** Only a reference: platform credentials are never copied into applications. */
@Data
public class ApplicationIntegrationConfig {
    @Positive
    private Long connectionId;
    @NotNull @PositiveOrZero
    private Long revision = 0L;
}
