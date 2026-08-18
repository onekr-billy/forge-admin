package com.mdframe.forge.plugin.generator.dto.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 应用分发配置；外部平台只保存受管连接器标识，不接收明文凭证。 */
@Data
public class BusinessApplicationDistributionDTO {

    /** WORKBENCH / DINGTALK。 */
    private String channel;

    /** CURRENT_USER / ROLES。 */
    private String targetType;

    private List<Long> roleIds = new ArrayList<>();

    private Boolean enabled;

    /** 受管连接器标识，凭证由集成中心维护。 */
    private String managedConnectorKey;
}
