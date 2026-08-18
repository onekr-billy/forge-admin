package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessTrigger;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerLogMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessTriggerMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("触发器 WEBHOOK 保存校验")
class BusinessTriggerServiceWebhookTest {

    @Test
    @DisplayName("只保存受管 EXTERNAL_API 配置")
    void validatesGovernedSourceBeforeInsert() {
        BusinessTriggerMapper mapper = mock(BusinessTriggerMapper.class);
        BusinessTriggerService service = new BusinessTriggerService(
                mapper, mock(BusinessTriggerLogMapper.class));
        AiBusinessTrigger valid = trigger("""
                {"sourceType":"EXTERNAL_API","sourceKey":"inventory/deduct",
                 "paramMappings":[],"resultMappings":[],"failureStrategy":"THROW"}
                """);

        service.insert(valid);
        verify(mapper).insert(any(AiBusinessTrigger.class));

        BusinessException error = assertThrows(BusinessException.class, () -> service.insert(trigger("""
                {"sourceType":"DATASET","sourceKey":"inventory_dataset"}
                """)));
        assertEquals("CALL_API 只允许调用 EXTERNAL_API 查询源", error.getMessage());
    }

    private AiBusinessTrigger trigger(String config) {
        AiBusinessTrigger trigger = new AiBusinessTrigger();
        trigger.setObjectCode("presale_order");
        trigger.setTriggerName("调用库存");
        trigger.setActionType("WEBHOOK");
        trigger.setActionConfig(config);
        return trigger;
    }
}
