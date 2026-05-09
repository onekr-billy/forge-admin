package com.mdframe.forge.starter.flow.vo;

import lombok.Data;

@Data
public class VersionRevertVO {
    private String newVersionId;
    private Integer newVersion;
    private String deploymentId;
    private Integer runningInstances;
}