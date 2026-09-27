package com.mdframe.forge.starter.flow.listener;

import com.mdframe.forge.starter.flow.service.FlowOrgIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Normalizes task user identities and resolves the process key for task mirrors. */
@Slf4j
final class FlowTaskIdentityResolver {

    private FlowTaskIdentityResolver() {
    }

    static String normalizeUserId(FlowOrgIntegrationService orgIntegrationService,
                                  String value, String taskId, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }
        String text = value.trim();
        if (isNumeric(text)) {
            return text;
        }
        if (orgIntegrationService == null) {
            log.warn("任务{}不是用户ID且组织集成不可用：taskId={}, {}={}", fieldName, taskId, fieldName, text);
            return text;
        }
        try {
            List<Map<String, Object>> users = orgIntegrationService.getUserList(text, null);
            List<String> exactUserIds = users.stream()
                    .filter(user -> matchesUser(text, user))
                    .map(user -> Objects.toString(user.get("id"), null))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (exactUserIds.size() == 1) {
                String userId = exactUserIds.get(0);
                log.info("任务{}已从显示值归一为用户ID：taskId={}, raw={}, userId={}", fieldName, taskId, text, userId);
                return userId;
            }
            log.warn("任务{}无法唯一归一为用户ID：taskId={}, raw={}, matches={}", fieldName, taskId, text, exactUserIds.size());
        } catch (Exception e) {
            log.warn("任务{}归一用户ID失败：taskId={}, raw={}", fieldName, taskId, text, e);
        }
        return text;
    }

    static String resolveUserDisplayName(FlowOrgIntegrationService orgIntegrationService,
                                         String userId, String fallback) {
        // The task mirror already has a display name in most updates; only resolve missing names.
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        if (userId != null && !userId.isBlank() && orgIntegrationService != null) {
            try {
                Map<String, Object> userInfo = orgIntegrationService.getUserInfo(userId.trim());
                if (userInfo != null) {
                    String name = firstNonBlank(
                            userInfo.get("realName"), userInfo.get("name"), userInfo.get("nickname"));
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }
            } catch (Exception e) {
                log.debug("反查流程用户姓名失败: userId={}", userId, e);
            }
        }
        return fallback != null && !fallback.isBlank()
                ? fallback.trim()
                : userId;
    }

    static String extractProcessKey(RepositoryService repositoryService, String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        // Standard IDs are processKey:version:id; UUID IDs require a repository lookup.
        int versionSeparator = processDefinitionId.indexOf(':');
        if (versionSeparator > 0) {
            return processDefinitionId.substring(0, versionSeparator);
        }
        if (repositoryService == null) {
            return processDefinitionId;
        }
        try {
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (definition != null && definition.getKey() != null && !definition.getKey().isBlank()) {
                return definition.getKey();
            }
        } catch (Exception e) {
            log.debug("从流程定义ID解析流程Key失败: processDefinitionId={}", processDefinitionId, e);
        }
        return processDefinitionId;
    }

    private static boolean matchesUser(String value, Map<String, Object> user) {
        if (user == null) {
            return false;
        }
        return value.equals(Objects.toString(user.get("id"), null))
                || value.equals(Objects.toString(user.get("username"), null))
                || value.equals(Objects.toString(user.get("name"), null))
                || value.equals(Objects.toString(user.get("realName"), null));
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }
}
