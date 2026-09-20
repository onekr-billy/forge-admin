package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.enums.DataAuditValueState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DataAuditNormalizedValue {

    DataAuditValueState state;
    String type;
    Object canonical;
    String display;
    int encodedSize;

    public static DataAuditNormalizedValue absent() {
        return DataAuditNormalizedValue.builder()
                .state(DataAuditValueState.ABSENT)
                .type("ABSENT")
                .canonical(null)
                .display(null)
                .encodedSize(0)
                .build();
    }

    public static DataAuditNormalizedValue nil(String type) {
        return DataAuditNormalizedValue.builder()
                .state(DataAuditValueState.NULL)
                .type(type == null ? "NULL" : type)
                .canonical(null)
                .display(null)
                .encodedSize(0)
                .build();
    }
}
