package com.mdframe.forge.plugin.generator.manager;

import com.mdframe.forge.plugin.generator.dto.audit.DataAuditRemoveDTO;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 动态 CRUD 写入用例编排器。
 *
 * <p>业务数据与事件 Outbox 必须在同一事务内写入；Controller 不再承担事件发布，
 * 从而避免数据已提交但进程尚未来得及发布事件的窗口。</p>
 */
@Component
@RequiredArgsConstructor
public class DynamicCrudMutationManager {

    private final DynamicCrudService records;
    private final BusinessEventPublisher events;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String configKey, Map<String, Object> data) {
        events.assertTransactionalPublishSupported(configKey);
        Map<String, Object> created = records.insert(configKey, data);
        Map<String, Object> result = created != null ? created : data;
        events.publishRecordCreated(configKey, result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(String configKey, Map<String, Object> data) {
        events.assertTransactionalPublishSupported(configKey);
        Map<String, Object> previousData = null;
        Object recordId = records.resolveRecordId(configKey, data);
        if (recordId != null) {
            previousData = records.selectById(configKey, recordId);
        }
        records.updateById(configKey, data);
        events.publishRecordUpdated(configKey, data, previousData);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String configKey, String id) {
        events.assertTransactionalPublishSupported(configKey);
        records.deleteById(configKey, id);
        events.publishRecordDeleted(configKey, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int remove(String configKey, DataAuditRemoveDTO dto) {
        events.assertTransactionalPublishSupported(configKey);
        int affected = records.removeWithAudit(configKey, dto);
        if (dto != null && dto.getIds() != null) {
            dto.getIds().forEach(id -> events.publishRecordDeleted(configKey, id));
        }
        return affected;
    }

    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(String configKey, List<String> ids) {
        events.assertTransactionalPublishSupported(configKey);
        int affected = records.batchDeleteByIds(configKey, ids);
        ids.forEach(id -> events.publishRecordDeleted(configKey, id));
        return affected;
    }
}
