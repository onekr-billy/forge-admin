package com.mdframe.forge.plugin.generator.manager;

import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/** 页面新增与开放填报的共同入口，字段校验、存储路由继续由动态 CRUD 负责。 */
@Component
@RequiredArgsConstructor
public class DynamicCrudCreateManager {
    private final DynamicCrudService records;
    private final BusinessEventPublisher events;
    private final PlatformTransactionManager transactionManager;

    public Map<String, Object> create(String configKey, Map<String, Object> data) {
        Map<String, Object> created = records.insert(configKey, data);
        Map<String, Object> result = created != null ? created : data;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    // afterCommit 时原连接仍绑定线程，先挂起，避免事件写入加入已提交的事务。
                    TransactionTemplate outside = new TransactionTemplate(transactionManager);
                    outside.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
                    outside.executeWithoutResult(status -> events.publishRecordCreated(configKey, result));
                }
            });
        } else {
            events.publishRecordCreated(configKey, result);
        }
        return result;
    }
}
