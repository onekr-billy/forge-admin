package com.mdframe.forge.starter.flow.dto;

import lombok.Data;

@Data
public class VersionCompareDTO {
    private String modelId;
    private Integer version1;
    private Integer version2;
}