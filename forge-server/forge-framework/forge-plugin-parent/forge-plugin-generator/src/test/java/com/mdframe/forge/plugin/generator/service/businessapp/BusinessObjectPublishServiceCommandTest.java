package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessPublishCheckLevel;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessPublishCheckItemVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectRelationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("业务对象发布检查 - 事务型命令")
class BusinessObjectPublishServiceCommandTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BusinessObjectPublishService service;
    private Method checkTransactionalActions;

    @BeforeEach
    void setUp() throws Exception {
        service = new BusinessObjectPublishService(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, objectMapper);
        checkTransactionalActions = BusinessObjectPublishService.class.getDeclaredMethod(
                "checkTransactionalActions",
                BusinessObjectDesignerService.DesignerContext.class,
                List.class);
        checkTransactionalActions.setAccessible(true);
    }

    @Test
    @DisplayName("合法本地事务命令通过专项协议检查")
    void acceptsValidLocalTransactionCommand() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("executionMode", "LOCAL_TRANSACTION");
        config.put("inputSchema", List.of(Map.of(
                "name", "quantity", "label", "数量", "type", "integer", "required", true, "min", 1)));
        config.put("steps", List.of(Map.of(
                "stepCode", "adjust_quantity",
                "stepType", "ADJUST_NUMBER",
                "rollbackOnFailure", true,
                "stepConfig", Map.of(
                        "targetConfigKey", "order_item",
                        "targetRecordIdField", "record.itemId",
                        "adjustments", List.of(Map.of(
                                "targetField", "pendingQuantity",
                                "sourceType", "form",
                                "sourceField", "quantity",
                                "operator", "SUBTRACT",
                                "min", 0))))));

        List<BusinessPublishCheckItemVO> items = validate(command("confirm_pickup", config));

        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("危险配置和本地事务外部步骤形成不可降级阻断")
    void rejectsDangerousAndNonLocalCommandAsBlock() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("executionMode", "LOCAL_TRANSACTION");
        config.put("headers", Map.of("Authorization", "secret"));
        config.put("steps", List.of(Map.of(
                "stepCode", "send_message",
                "stepType", "SEND_MESSAGE",
                "rollbackOnFailure", true,
                "stepConfig", Map.of())));

        List<BusinessPublishCheckItemVO> items = validate(command("confirm_pickup", config));

        assertEquals(1, items.size());
        assertEquals("COMMAND_PROTOCOL_INVALID", items.get(0).getItemCode());
        assertEquals("COMMAND", items.get(0).getCategory());
        assertEquals(BusinessPublishCheckLevel.BLOCK, items.get(0).getLevel());
    }

    @Test
    @DisplayName("合法子表行命令通过关系绑定检查")
    void acceptsChildRowCommandBoundToPublishedRelation() throws Exception {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("triggerScene", "MANUAL");
        config.put("relationKey", "order_item");
        config.put("executionMode", "LOCAL_TRANSACTION");
        config.put("inputSchema", List.of());
        config.put("steps", List.of(Map.of(
                "stepCode", "update_detail",
                "stepType", "UPDATE_FIELD",
                "rollbackOnFailure", true,
                "stepConfig", Map.of(
                        "targetConfigKey", "order_item",
                        "targetRecordIdField", "record.id",
                        "fieldMappings", List.of(Map.of(
                                "targetField", "status",
                                "value", "CONFIRMED"))))));
        Map<String, Object> action = new LinkedHashMap<>(command("confirm_detail", config));
        action.put("actionPosition", "CHILD_ROW");

        List<BusinessPublishCheckItemVO> items = validate(action, List.of(childRelation()));

        assertTrue(items.isEmpty());
    }

    @Test
    @DisplayName("子表行按钮拒绝非命令类型")
    void rejectsNonCommandChildRowAction() throws Exception {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionCode", "open_detail");
        action.put("actionName", "打开明细");
        action.put("actionPosition", "CHILD_ROW");
        action.put("actionType", "OPEN_PAGE");
        action.put("actionConfig", Map.of("triggerScene", "MANUAL", "relationKey", "order_item"));

        List<BusinessPublishCheckItemVO> items = validate(action, List.of(childRelation()));

        assertEquals(1, items.size());
        assertEquals("COMMAND_PROTOCOL_INVALID", items.get(0).getItemCode());
        assertTrue(items.get(0).getMessage().contains("仅支持 COMMAND"));
    }

    @Test
    @DisplayName("受管外部接口步骤通过发布检查，数据集调用被阻断")
    void validatesCallApiStepDuringPublish() throws Exception {
        Map<String, Object> validConfig = new LinkedHashMap<>();
        validConfig.put("executionMode", "ORCHESTRATION");
        validConfig.put("steps", List.of(Map.of(
                "stepCode", "call_inventory",
                "stepType", "CALL_API",
                "rollbackOnFailure", true,
                "stepConfig", Map.of(
                        "sourceType", "EXTERNAL_API",
                        "sourceKey", "inventory/deduct",
                        "paramMappings", List.of(Map.of(
                                "param", "sku",
                                "sourceType", "record",
                                "sourceField", "skuCode")),
                        "resultMappings", List.of(),
                        "failureStrategy", "THROW"))));
        assertTrue(validate(command("deduct_inventory", validConfig)).isEmpty());

        Map<String, Object> invalidConfig = new LinkedHashMap<>(validConfig);
        invalidConfig.put("steps", List.of(Map.of(
                "stepCode", "call_dataset",
                "stepType", "CALL_API",
                "rollbackOnFailure", true,
                "stepConfig", Map.of(
                        "sourceType", "DATASET",
                        "sourceKey", "inventory_dataset"))));
        List<BusinessPublishCheckItemVO> items = validate(command("call_dataset", invalidConfig));
        assertEquals(1, items.size());
        assertEquals("COMMAND_PROTOCOL_INVALID", items.get(0).getItemCode());
    }

    private List<BusinessPublishCheckItemVO> validate(Map<String, Object> action) throws Exception {
        return validate(action, List.of());
    }

    private List<BusinessPublishCheckItemVO> validate(
            Map<String, Object> action,
            List<BusinessObjectRelationVO> relations) throws Exception {
        AiBusinessObject object = new AiBusinessObject();
        object.setObjectCode("order");
        object.setDesignerOptions(objectMapper.writeValueAsString(Map.of("actions", List.of(action))));
        BusinessObjectDesignerService.DesignerContext context = new BusinessObjectDesignerService.DesignerContext();
        context.setObject(object);
        context.setRelations(relations);
        List<BusinessPublishCheckItemVO> items = new ArrayList<>();
        checkTransactionalActions.invoke(service, context, items);
        return items;
    }

    private BusinessObjectRelationVO childRelation() {
        BusinessObjectRelationVO relation = new BusinessObjectRelationVO();
        relation.setSourceObjectCode("order");
        relation.setTargetObjectCode("ORDER_ITEM");
        relation.setRelationType("DETAIL");
        relation.setRelationConfig("{\"relationKey\":\"order_item\"}");
        relation.setStatus(1);
        return relation;
    }

    private Map<String, Object> command(String actionCode, Map<String, Object> config) {
        return Map.of(
                "actionCode", actionCode,
                "actionName", "确认提货",
                "actionType", "COMMAND",
                "actionConfig", config);
    }
}
