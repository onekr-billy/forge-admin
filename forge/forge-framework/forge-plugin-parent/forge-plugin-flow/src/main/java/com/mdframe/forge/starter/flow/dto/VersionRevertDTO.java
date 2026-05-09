package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

@Data
public class VersionRevertDTO {
    private String modelId;
    private Integer targetVersion;
    private String changeDescription;
}