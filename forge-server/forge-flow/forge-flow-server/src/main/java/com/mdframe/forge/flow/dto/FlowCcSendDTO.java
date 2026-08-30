package com.mdframe.forge.flow.dto;

import lombok.Data;

import java.util.List;

/**
 * 发送流程抄送请求。
 */
@Data
public class FlowCcSendDTO {

    private String processInstanceId;

    private String processDefKey;

    private String taskId;

    private String title;

    private String content;

    private String businessKey;

    private List<String> ccUserIds;

    private List<String> ccUserNames;

    private String sendUserId;

    private String sendUserName;
}
