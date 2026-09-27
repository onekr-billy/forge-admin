package com.mdframe.forge.admin.integration.dto;

import lombok.Data;

/** Published capability whose source may belong to an application's published snapshot. */
@Data
public class ApplicationCapabilityCandidate {
    private Long capabilityId;
    private String capabilityCode;
    private String sourceType;
    private String sourceKey;
    private String policySnapshot;
}
