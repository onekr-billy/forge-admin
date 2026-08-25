package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.message.domain.dto.MessageSendRequestDTO;
import com.mdframe.forge.plugin.message.domain.entity.SysMessage;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SendMessageActionStepExecutor implements BusinessActionStepExecutor {

    private final BusinessMessageChannelService messageChannelService;

    @Override
    public String supportType() {
        return "SEND_MESSAGE";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, BusinessActionStepDTO step) {
        Map<String, Object> config = step.getStepConfig();
        String templateCode = BusinessActionStepConfigHelper.firstText(config, "templateCode");
        if (StringUtils.isBlank(templateCode)) {
            throw new BusinessException("发送消息步骤缺少 templateCode");
        }
        String receiverRule = BusinessActionStepConfigHelper.firstText(config, "receiverRule");
        String channelCode = BusinessActionStepConfigHelper.firstText(config, "channelCode", "channel");
        BusinessMessageChannelStatus channelStatus = messageChannelService.resolveChannel(channelCode);
        if (Boolean.TRUE.equals(channelStatus.getTodo())) {
            JSONObject todo = messageChannelService.buildThirdPartyTodoResult(
                    channelStatus.getChannelType(), channelStatus.getChannelCode());
            return todoResult(step, todo);
        }

        MessageSendRequestDTO request = new MessageSendRequestDTO();
        request.setTemplateCode(templateCode);
        request.setChannel(StringUtils.defaultIfBlank(channelStatus.getSendChannel(), "WEB"));
        request.setType("SYSTEM");
        request.setBizType("BUSINESS_ACTION");
        request.setBizKey(context.getRequest().getObjectCode() + ":" + context.getRequest().getRecordId());
        request.setParams(resolveMessageParams(context));
        resolveReceivers(request, receiverRule, context);

        SysMessage message = messageChannelService.sendInternalMessage(request);
        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus(com.mdframe.forge.plugin.generator.enums.BusinessActionStepStatus.SUCCESS.getCode());
        result.setMessage("消息已发送");
        result.getResult().put("templateCode", templateCode);
        result.getResult().put("channel", channelStatus.getChannelCode());
        result.getResult().put("messageId", message == null ? null : message.getId());
        return result;
    }

    private BusinessActionStepResultVO todoResult(BusinessActionStepDTO step, JSONObject todo) {
        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStepCode(step.getStepCode());
        result.setStepName(step.getStepName());
        result.setStepType(step.getStepType());
        result.setStatus(com.mdframe.forge.plugin.generator.enums.BusinessActionStepStatus.TODO.getCode());
        result.setMessage(todo.getString("message"));
        result.getResult().putAll(todo);
        return result;
    }

    private Map<String, Object> resolveMessageParams(BusinessActionExecutionContext context) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (context.getRecordData() != null) {
            params.putAll(context.getRecordData());
        }
        if (context.getFormData() != null) {
            params.put("formData", context.getFormData());
            params.putAll(context.getFormData());
        }
        params.put("objectCode", context.getRequest().getObjectCode());
        params.put("recordId", context.getRequest().getRecordId());
        params.put("actionCode", context.getAction() == null ? null : context.getAction().getActionCode());
        params.put("correlationId", context.getCorrelationId());
        return params;
    }

    private void resolveReceivers(MessageSendRequestDTO request, String receiverRule, BusinessActionExecutionContext context) {
        if (StringUtils.isBlank(receiverRule)) {
            setSingleReceiver(request, resolveUserId());
            return;
        }
        String normalizedRule = receiverRule.trim().toUpperCase(Locale.ROOT);
        if ("STARTER".equals(normalizedRule)) {
            setSingleReceiver(request, resolveUserId());
        } else if ("OWNER".equals(normalizedRule)) {
            Long ownerId = firstLong(context.getRecordData(), "ownerId", "owner_id", "responsibleId", "responsible_id", "assigneeId", "assignee_id");
            setSingleReceiver(request, ownerId == null ? resolveUserId() : ownerId);
        } else if ("CREATOR".equals(normalizedRule)) {
            Long creatorId = firstLong(context.getRecordData(), "createBy", "create_by");
            setSingleReceiver(request, creatorId == null ? resolveUserId() : creatorId);
        } else if (normalizedRule.startsWith("USERS:")) {
            request.setUserIds(new LinkedHashSet<>(parseLongList(receiverRule.substring(receiverRule.indexOf(':') + 1))));
            request.setSendScope("USERS");
        } else if (normalizedRule.startsWith("ROLES:")) {
            List<Long> roleIds = parseLongList(receiverRule.substring(receiverRule.indexOf(':') + 1));
            Long orgId = resolveBusinessOrgId(context);
            request.setUserIds(messageChannelService.toUserIdSet(
                    messageChannelService.selectUserIdsByRoleIds(roleIds, context.getTenantId(), orgId)));
            request.setSendScope("USERS");
        } else if (normalizedRule.startsWith("DEPTS:")) {
            request.setOrgIds(new LinkedHashSet<>(parseLongList(receiverRule.substring(receiverRule.indexOf(':') + 1))));
            request.setSendScope("ORG");
        } else if ("ALL".equals(normalizedRule)) {
            request.setSendScope("ALL");
        } else {
            setSingleReceiver(request, resolveUserId());
        }
    }

    private Long resolveBusinessOrgId(BusinessActionExecutionContext context) {
        Long orgId = firstLong(context.getRecordData(),
                "activeOrgId", "orgId", "org_id", "deptId", "dept_id", "createDept", "create_dept",
                "main.activeOrgId", "main.orgId", "main.org_id", "main.deptId", "main.dept_id",
                "main.createDept", "main.create_dept");
        if (orgId != null) {
            return orgId;
        }
        orgId = firstLong(context.getFormData(),
                "activeOrgId", "orgId", "org_id", "deptId", "dept_id", "createDept", "create_dept");
        if (orgId != null) {
            return orgId;
        }
        return firstLong(context.getExtraContext(),
                "activeOrgId", "orgId", "org_id", "deptId", "dept_id", "createDept", "create_dept");
    }

    private void setSingleReceiver(MessageSendRequestDTO request, Long userId) {
        if (userId != null) {
            request.setUserIds(Set.of(userId));
        }
        request.setSendScope("USERS");
    }

    private Long firstLong(Map<String, Object> recordData, String... fields) {
        if (recordData == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            Object value = BusinessActionStepConfigHelper.readPath(recordData, field);
            if (value == null) {
                continue;
            }
            try {
                return Long.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // ignore invalid receiver id
            }
        }
        return null;
    }

    private List<Long> parseLongList(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        List<Long> ids = new java.util.ArrayList<>();
        for (String item : value.split(",")) {
            try {
                ids.add(Long.valueOf(item.trim()));
            } catch (NumberFormatException ignored) {
                // ignore invalid receiver id
            }
        }
        return ids;
    }

    private Long resolveUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception e) {
            return 1L;
        }
    }
}
