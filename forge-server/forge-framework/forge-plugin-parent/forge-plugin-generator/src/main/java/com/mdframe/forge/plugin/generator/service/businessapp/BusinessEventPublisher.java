package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.mapper.AiCrudConfigMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业务事件发布者。
 * <p>
 * 在 DynamicCrudController 的增删改操作后调用此服务发布业务事件，
 * 交由触发器引擎异步处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEventPublisher {

    private final BusinessTriggerExecutor triggerExecutor;
    private final AiCrudConfigMapper crudConfigMapper;
    private final BusinessObjectMapper businessObjectMapper;
    private final DynamicCrudService dynamicCrudService;
    private final ObjectProvider<BusinessProcessOrchestrator> processOrchestratorProvider;

    /**
     * 发布记录创建事件
     */
    public void publishRecordCreated(String configKey, Map<String, Object> data) {
        BusinessEvent event = buildEvent(configKey, BusinessEvent.RECORD_CREATED, data, null);
        publish(event);
        publish(buildEvent(configKey, BusinessEvent.FORM_SUBMITTED, data, null));
    }

    /**
     * 发布记录更新事件
     */
    public void publishRecordUpdated(String configKey, Map<String, Object> data, Map<String, Object> previousData) {
        BusinessEvent event = buildEvent(configKey, BusinessEvent.RECORD_UPDATED, data, previousData);
        if (event != null) {
            publish(event);

            // 检查是否有状态变更
            if (previousData != null && data != null) {
                checkStatusChange(event, data, previousData);
            }
            publish(buildEvent(configKey, BusinessEvent.FORM_SUBMITTED, data, previousData));
        }
    }

    /**
     * 发布记录删除事件
     */
    public void publishRecordDeleted(String configKey, String recordId) {
        BusinessEvent event = buildEvent(configKey, BusinessEvent.RECORD_DELETED, null, null);
        if (event != null) {
            event.setRecordId(recordId);
            publish(event);
        }
    }

    public void publishFlowApproved(String objectCode, String recordId, Map<String, Object> recordData) {
        publish(buildFlowResultEvent(objectCode, recordId, BusinessEvent.FLOW_APPROVED, recordData));
    }

    public void publishFlowRejected(String objectCode, String recordId, Map<String, Object> recordData) {
        publish(buildFlowResultEvent(objectCode, recordId, BusinessEvent.FLOW_REJECTED, recordData));
    }

    /**
     * 接收流程回调等内部运行态发布的业务事件。
     */
    @EventListener
    public void publishFlowEvent(BusinessEvent event) {
        if (event == null || StringUtils.isBlank(event.getEventType())) {
            return;
        }
        if (BusinessEvent.FLOW_APPROVED.equals(event.getEventType())
                || BusinessEvent.FLOW_REJECTED.equals(event.getEventType())
                || BusinessEvent.FLOW_CANCELED.equals(event.getEventType())) {
            publish(event);
        }
    }

    /**
     * 检查状态字段变更，如有变更则额外发布 STATUS_CHANGED 事件
     */
    private void checkStatusChange(BusinessEvent baseEvent, Map<String, Object> data, Map<String, Object> previousData) {
        // 常见状态字段名
        String[] statusFields = {"status", "state", "audit_status", "approval_status", "documentStatus", "document_status"};
        for (String field : statusFields) {
            Object newVal = baseEvent.readRecordValue(field);
            Object oldVal = baseEvent.readPreviousValue(field);
            if (newVal != null && !newVal.equals(oldVal)) {
                BusinessEvent statusEvent = BusinessEvent.builder()
                        .eventType(BusinessEvent.STATUS_CHANGED)
                        .suiteCode(baseEvent.getSuiteCode())
                        .objectCode(baseEvent.getObjectCode())
                        .configKey(baseEvent.getConfigKey())
                        .recordId(baseEvent.getRecordId())
                        .recordData(data)
                        .previousData(previousData)
                        .operatorId(baseEvent.getOperatorId())
                        .operatorName(baseEvent.getOperatorName())
                        .tenantId(baseEvent.getTenantId())
                        .build();
                publish(statusEvent);
                break;
            }
        }
    }

    private void publish(BusinessEvent event) {
        if (event == null || StringUtils.isBlank(event.getObjectCode()) || StringUtils.isBlank(event.getEventType())) {
            return;
        }
        // Keep legacy action triggers and application-level START_EVENT flows
        // on the same successful CRUD event. The orchestrator is optional so
        // installations that do not enable business-process support keep the
        // existing trigger behavior.
        triggerExecutor.executeTriggersAsync(event);
        BusinessProcessOrchestrator orchestrator = processOrchestratorProvider.getIfAvailable();
        if (orchestrator != null) {
            try {
                orchestrator.startEvent(event);
            } catch (Exception exception) {
                // The business row is already committed. A transient process
                // start failure is recorded in logs and can be retried from
                // the process runtime; it must not turn a successful save
                // into a misleading CRUD error response.
                log.error("事件开始业务流程失败, objectCode={}, eventType={}, recordId={}",
                        event.getObjectCode(), event.getEventType(), event.getRecordId(), exception);
            }
        }
    }

    private BusinessEvent buildFlowResultEvent(String objectCode, String recordId,
                                               String eventType, Map<String, Object> recordData) {
        try {
            return BusinessEvent.builder()
                    .eventType(eventType)
                    .objectCode(objectCode)
                    .recordId(recordId)
                    .recordData(recordData)
                    .operatorId(resolveUserId())
                    .operatorName(resolveUsername())
                    .tenantId(resolveTenantId())
                    .build();
        } catch (Exception e) {
            log.debug("构建流程业务事件失败, objectCode={}, recordId={}", objectCode, recordId);
            return null;
        }
    }

    private Long resolveTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception e) {
            tenantId = null;
        }
        return tenantId != null ? tenantId : 1L;
    }

    private Long resolveUserId() {
        try {
            return SessionHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUsername() {
        try {
            return SessionHelper.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据 configKey 构建事件
     */
    private BusinessEvent buildEvent(String configKey, String eventType, Map<String, Object> data, Map<String, Object> previousData) {
        try {
            Long tenantId = SessionHelper.getTenantId();

            // 从运行配置中获取对象编码
            ResolvedBusinessObject resolvedObject = resolveObject(configKey, tenantId);
            String objectCode = resolvedObject == null ? null : resolvedObject.objectCode();
            if (objectCode == null) {
                return null; // 非业务对象的动态CRUD，不触发
            }

            String recordId = resolveRecordId(configKey, data);

            return BusinessEvent.builder()
                    .eventType(eventType)
                    .suiteCode(resolvedObject.suiteCode())
                    .objectCode(objectCode)
                    .configKey(configKey)
                    .recordId(recordId)
                    .recordData(data)
                    .previousData(previousData)
                    .operatorId(SessionHelper.getUserId())
                    .operatorName(SessionHelper.getUsername())
                    .tenantId(tenantId)
                    .build();
        } catch (Exception e) {
            log.debug("构建业务事件失败, configKey={}: {}", configKey, e.getMessage());
            return null;
        }
    }

    private String resolveRecordId(String configKey, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            Object recordId = dynamicCrudService.resolveRecordId(configKey, data);
            if (recordId != null) {
                return String.valueOf(recordId);
            }
        } catch (Exception e) {
            log.debug("按运行主键解析业务事件记录ID失败, configKey={}: {}", configKey, e.getMessage());
        }
        Object fallback = data.get("id");
        if (fallback == null) {
            fallback = data.get("Id");
        }
        return fallback == null ? null : String.valueOf(fallback);
    }

    /**
     * 通过 configKey 查询关联的业务对象编码
     */
    private ResolvedBusinessObject resolveObject(String configKey, Long tenantId) {
        try {
            // 业务对象表的 object_code 才是流程/触发器绑定使用的规范编码。
            // ai_crud_config.object_code 在历史低代码数据中通常保存模型/配置编码，
            // 直接使用它会导致事件无法命中已发布流程的 subject_object_code。
            var businessObject = businessObjectMapper.selectByConfigKey(tenantId, configKey);
            if (businessObject != null && StringUtils.isNotBlank(businessObject.getObjectCode())) {
                return new ResolvedBusinessObject(
                        businessObject.getSuiteCode(), businessObject.getObjectCode());
            }
            // 兼容尚未迁移到业务对象表的旧 CONFIG 配置。
            AiCrudConfig config = crudConfigMapper.selectByConfigKey(tenantId, configKey);
            if (config != null && config.getObjectCode() != null && !config.getObjectCode().isBlank()) {
                return new ResolvedBusinessObject(config.getDomainCode(), config.getObjectCode());
            }
        } catch (Exception e) {
            log.debug("resolveObject 失败: configKey={}", configKey);
        }
        return null;
    }

    private record ResolvedBusinessObject(String suiteCode, String objectCode) {
    }
}
