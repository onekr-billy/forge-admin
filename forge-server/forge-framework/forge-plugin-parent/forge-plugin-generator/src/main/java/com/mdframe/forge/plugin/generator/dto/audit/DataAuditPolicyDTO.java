package com.mdframe.forge.plugin.generator.dto.audit;

import lombok.Data;

@Data
public class DataAuditPolicyDTO {

    private Boolean enabled;

    private Boolean reasonRequired;

    private Boolean showInDetail;
}
