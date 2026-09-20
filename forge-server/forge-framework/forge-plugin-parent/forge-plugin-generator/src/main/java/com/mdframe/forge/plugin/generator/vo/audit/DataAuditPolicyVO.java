package com.mdframe.forge.plugin.generator.vo.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DataAuditPolicyVO {

    private String objectId;

    private String objectCode;

    private String objectName;

    private Boolean enabled;

    private Boolean reasonRequired;

    private Boolean showInDetail;

    private Integer policyVersion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enabledAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String coverageStatus;

    private List<DataAuditCoverageItemVO> coverageItems = new ArrayList<>();

    private String message;
}
