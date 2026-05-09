package com.mdframe.forge.starter.flow.vo;

import lombok.Data;

@Data
public class VersionDetailVO {
    private String id;
    private String modelId;
    private Integer version;
    private String versionName;
    private String versionTag;
    private String bpmnXml;
    private String formJson;
    private String changeDescription;
    private String deploymentId;
    private String processDefinitionId;
    private String publishBy;
    private String publishTime;
}