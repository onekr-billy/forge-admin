package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/**
 * 待办任务表单访问策略。
 * <p>
 * 以 Policy Object 统一任务身份、认领状态和请求上下文一致性校验；不读取会话，
 * 不调用流程引擎，也不修改任务状态。
 */
@Slf4j
final class BusinessFlowTaskAccessPolicy {

    void validate(BusinessTaskFormContextQueryDTO query,
                  boolean writeRequired,
                  Map<String, Object> task,
                  boolean flowServiceConfigured,
                  Long currentUserId) {
        if (query == null) {
            throw new BusinessException("业务待办表单参数不能为空");
        }
        String taskId = StringUtils.trimToNull(query.getTaskId());
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        query.setTaskId(taskId);
        if (!flowServiceConfigured) {
            throw new BusinessException("流程服务未配置，无法校验任务身份");
        }
        if (currentUserId == null) {
            throw new BusinessException("当前登录用户不能为空");
        }
        if (task == null || task.isEmpty()) {
            throw new BusinessException("任务不存在或无权访问");
        }

        Integer status = readIntegerValue(task.get("status"));
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("当前任务已处理，不能访问待办业务表单");
        }

        String currentUser = String.valueOf(currentUserId);
        String assignee = StringUtils.trimToNull(textValue(task.get("assignee")));
        boolean claimedByCurrentUser = StringUtils.equals(assignee, currentUser);
        boolean unclaimedCandidate = StringUtils.isBlank(assignee)
                && (csvContains(task.get("candidateUsers"), currentUser)
                || StringUtils.isNotBlank(textValue(task.get("candidateGroups"))));

        if (!claimedByCurrentUser && !unclaimedCandidate) {
            throw new BusinessException("无权访问当前任务业务表单");
        }
        if (writeRequired && !claimedByCurrentUser) {
            throw new BusinessException("请先签收任务后再保存业务字段");
        }

        assertTaskFieldMatches(query.getProcessInstanceId(), task.get("processInstanceId"), "流程实例");
        assertBusinessKeyMatches(query.getBusinessKey(), task.get("businessKey"));
        assertTaskFieldMatches(query.getTaskDefKey(), task.get("taskDefKey"), "任务节点");
        assertProcessDefinitionMatches(query.getProcessDefKey(), task.get("processDefKey"));
        hydrateTrustedIdentity(query, task);
    }

    static void assertBusinessKeyMatches(String requestedValue, Object actualValue) {
        String requested = StringUtils.trimToNull(requestedValue);
        String actual = StringUtils.trimToNull(textValue(actualValue));
        if (requested == null || actual == null || StringUtils.equals(requested, actual)) {
            return;
        }
        if (isSameDocumentBusinessKey(requested, actual) || isSyntheticTestBusinessKey(actual)) {
            return;
        }
        throw new BusinessException("业务Key与当前任务不匹配");
    }

    static boolean isSyntheticTestBusinessKey(String businessKey) {
        String text = StringUtils.trimToNull(businessKey);
        return text != null && (text.startsWith("FLOW_TEST:") || "FLOW_TEST".equals(text));
    }

    private void hydrateTrustedIdentity(BusinessTaskFormContextQueryDTO query, Map<String, Object> task) {
        if (StringUtils.isBlank(query.getProcessInstanceId())) {
            query.setProcessInstanceId(StringUtils.trimToNull(textValue(task.get("processInstanceId"))));
        }
        String taskBusinessKey = StringUtils.trimToNull(textValue(task.get("businessKey")));
        if (isSyntheticTestBusinessKey(taskBusinessKey)) {
            // 发起测试尚未绑定低代码单据，不能用请求里的 objectCode:id 覆盖任务 Key。
            query.setBusinessKey(taskBusinessKey);
            if (query.getRecordId() != null && StringUtils.isBlank(query.getProcessInstanceId())) {
                query.setRecordId(null);
            }
        } else if (StringUtils.isBlank(query.getBusinessKey())) {
            query.setBusinessKey(taskBusinessKey);
        }
        if (StringUtils.isBlank(query.getTaskDefKey())) {
            query.setTaskDefKey(StringUtils.trimToNull(textValue(task.get("taskDefKey"))));
        }
        if (StringUtils.isBlank(query.getProcessDefKey())) {
            query.setProcessDefKey(StringUtils.trimToNull(textValue(task.get("processDefKey"))));
        }
    }

    private void assertTaskFieldMatches(String requestedValue, Object actualValue, String label) {
        String requested = StringUtils.trimToNull(requestedValue);
        String actual = StringUtils.trimToNull(textValue(actualValue));
        if (requested != null && actual != null && !StringUtils.equals(requested, actual)) {
            throw new BusinessException(label + "与当前任务不匹配");
        }
    }

    private static boolean isSameDocumentBusinessKey(String left, String right) {
        String leftKey = normalizeDocumentBusinessKey(left);
        String rightKey = normalizeDocumentBusinessKey(right);
        return StringUtils.isNotBlank(leftKey) && StringUtils.equals(leftKey, rightKey);
    }

    private static String normalizeDocumentBusinessKey(String businessKey) {
        String text = StringUtils.trimToNull(businessKey);
        if (text == null) {
            return null;
        }
        int retryIndex = text.indexOf(":R");
        if (retryIndex <= 0) {
            return text;
        }
        String retryNo = text.substring(retryIndex + 2);
        return StringUtils.isNumeric(retryNo) ? text.substring(0, retryIndex) : text;
    }

    private void assertProcessDefinitionMatches(String requestedValue, Object actualValue) {
        String requested = StringUtils.trimToNull(requestedValue);
        String actual = StringUtils.trimToNull(textValue(actualValue));
        if (requested == null || actual == null || StringUtils.equals(requested, actual)) {
            return;
        }
        String requestedKey = extractProcessDefinitionKey(requested);
        String actualKey = extractProcessDefinitionKey(actual);
        if (StringUtils.isNotBlank(requestedKey) && StringUtils.equals(requestedKey, actualKey)) {
            return;
        }
        if (isUuidLike(requested) || isUuidLike(actual)) {
            return;
        }
        log.debug("忽略流程定义标识表示差异: requested={}, actual={}", requested, actual);
    }

    private String extractProcessDefinitionKey(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null) {
            return null;
        }
        int separator = text.indexOf(':');
        return separator > 0 ? text.substring(0, separator) : text;
    }

    private boolean isUuidLike(String value) {
        String text = StringUtils.trimToNull(value);
        if (text == null || text.length() != 36) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (ch != '-') {
                    return false;
                }
                continue;
            }
            boolean hex = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private boolean csvContains(Object csvValue, String expected) {
        String csv = StringUtils.trimToNull(textValue(csvValue));
        if (csv == null || StringUtils.isBlank(expected)) {
            return false;
        }
        for (String part : csv.split(",")) {
            if (StringUtils.equals(StringUtils.trimToEmpty(part), expected)) {
                return true;
            }
        }
        return false;
    }

    private Integer readIntegerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
