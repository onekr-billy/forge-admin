package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessActionStepDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessObjectActionVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BusinessActionCommandPolicy")
class BusinessActionCommandPolicyTest {

    @Test
    @DisplayName("projects declared inputs and converts numeric values")
    void projectsDeclaredInputsAndConvertsNumericValues() {
        BusinessObjectActionVO action = action(Map.of(
                "inputSchema", List.of(Map.of(
                        "name", "quantity",
                        "type", "number",
                        "required", true,
                        "min", 1
                ))
        ));

        Map<String, Object> projected = BusinessActionCommandPolicy.projectFormData(
                action, Map.of("quantity", "2.5"));

        assertEquals(new BigDecimal("2.5"), projected.get("quantity"));
    }

    @Test
    @DisplayName("rejects undeclared and trusted identity inputs")
    void rejectsUndeclaredAndTrustedIdentityInputs() {
        BusinessObjectActionVO action = action(Map.of(
                "inputSchema", List.of(Map.of("name", "quantity", "type", "integer"))
        ));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.projectFormData(
                action, Map.of("quantity", 1, "extra", "x")));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.projectFormData(
                action, Map.of("tenantId", 9)));
    }

    @Test
    @DisplayName("distinguishes explicit empty input schema from legacy missing schema")
    void distinguishesExplicitEmptyInputSchemaFromLegacyMissingSchema() {
        BusinessObjectActionVO explicitEmpty = action(Map.of("inputSchema", List.of()));
        BusinessObjectActionVO legacy = action(Map.of());

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.projectFormData(
                explicitEmpty, Map.of("unexpected", "value")));
        assertEquals(Map.of("legacyField", "value"), BusinessActionCommandPolicy.projectFormData(
                legacy, Map.of("legacyField", "value")));
    }

    @Test
    @DisplayName("keeps route query only and drops browser row context")
    void keepsRouteQueryOnlyAndDropsBrowserRowContext() {
        Map<String, Object> sanitized = BusinessActionCommandPolicy.sanitizeClientContext(Map.of(
                "routeQuery", Map.of("scene", "pickup"),
                "row", Map.of("tenantId", 9, "status", "FORGED")
        ));

        assertEquals(Map.of("routeQuery", Map.of("scene", "pickup")), sanitized);
        assertFalse(sanitized.containsKey("row"));
    }

    @Test
    @DisplayName("local transaction recursively rejects non-local steps")
    void localTransactionRecursivelyRejectsNonLocalSteps() {
        BusinessActionStepDTO foreach = new BusinessActionStepDTO();
        foreach.setStepType("FOREACH");
        foreach.setRollbackOnFailure(true);
        foreach.setStepConfig(Map.of("steps", List.of(Map.of(
                "stepCode", "start_flow",
                "stepType", "START_FLOW",
                "rollbackOnFailure", true
        ))));

        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessActionCommandPolicy.validateExecutionMode("LOCAL_TRANSACTION", List.of(foreach)));

        assertEquals("本地事务动作不允许步骤类型: START_FLOW", error.getMessage());
    }

    @Test
    @DisplayName("local transaction requires rollback on every step")
    void localTransactionRequiresRollbackOnEveryStep() {
        BusinessActionStepDTO update = new BusinessActionStepDTO();
        update.setStepType("UPDATE_FIELD");
        update.setRollbackOnFailure(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> BusinessActionCommandPolicy.validateExecutionMode("LOCAL_TRANSACTION", List.of(update)));

        assertEquals("本地事务步骤必须在失败时回滚: UPDATE_FIELD", error.getMessage());
    }

    @Test
    @DisplayName("rejects protected target fields and untrusted context paths")
    void rejectsProtectedTargetsAndUntrustedContextPaths() {
        BusinessActionStepDTO protectedTarget = step("UPDATE_FIELD", Map.of(
                "fieldMappings", List.of(Map.of(
                        "targetField", "tenantId", "sourceType", "form", "sourceField", "quantity"))));
        BusinessActionStepDTO untrustedContext = step("UPDATE_FIELD", Map.of(
                "fieldMappings", List.of(Map.of(
                        "targetField", "status", "sourceType", "context", "sourceField", "currentUser.userId"))));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(protectedTarget)));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(untrustedContext)));
    }

    @Test
    @DisplayName("allows whitelisted trusted system values")
    void allowsWhitelistedTrustedSystemValues() {
        BusinessActionStepDTO update = step("UPDATE_FIELD", Map.of(
                "fieldMappings", List.of(Map.of(
                        "targetField", "ownerId", "sourceType", "system", "sourceField", "userId"))));

        BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(update));
    }

    @Test
    @DisplayName("converts MONEY input to minor units without rounding")
    void convertsMoneyInputToMinorUnitsWithoutRounding() {
        BusinessObjectActionVO action = action(Map.of(
                "inputSchema", List.of(Map.of("name", "cashAmount", "type", "money", "scale", 2))));

        Map<String, Object> projected = BusinessActionCommandPolicy.projectFormData(
                action, Map.of("cashAmount", "12.30"));
        assertEquals(1230L, projected.get("cashAmount"));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.projectFormData(
                action, Map.of("cashAmount", "12.345")));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.projectFormData(
                action, Map.of("cashAmount", "-0.01")));
    }

    @Test
    @DisplayName("requires structured status transition values")
    void requiresStructuredStatusTransitionValues() {
        BusinessActionStepDTO transition = step("TRANSITION_STATUS", Map.of(
                "targetConfigKey", "pre_sale_order",
                "targetRecordIdField", "record.id",
                "statusField", "status",
                "fromValue", "DRAFT",
                "toValue", "SUBMITTED"));
        BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(transition));

        BusinessActionStepDTO invalid = step("TRANSITION_STATUS", Map.of(
                "statusField", "status", "fromValue", "DRAFT", "toValue", "DRAFT"));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(invalid)));
    }

    @Test
    @DisplayName("allows ASSERT_RECORD state gates with expected field mappings")
    void allowsAssertRecordStateGatesWithExpectedFieldMappings() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "targetConfigKey", "pre_sale_order",
                "targetRecordIdField", "record.id",
                "expectedFieldMappings", List.of(Map.of(
                        "targetField", "status",
                        "value", "DRAFT"))));

        BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate));
    }

    @Test
    @DisplayName("fails closed for protected ASSERT_RECORD condition fields")
    void rejectsProtectedAssertRecordConditionFields() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "targetConfigKey", "pre_sale_order",
                "targetRecordIdField", "record.id",
                "expectedFieldMappings", List.of(Map.of(
                        "targetField", "tenantId",
                        "value", 1))));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate)));
    }

    @Test
    @DisplayName("allows numeric ASSERT_RECORD constraints from declared form values")
    void allowsNumericAssertRecordConstraints() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "targetConfigKey", "pre_sale_item",
                "targetRecordIdField", "record.id",
                "numericConstraints", List.of(Map.of(
                        "field", "pickedQuantity",
                        "operator", "gte",
                        "sourceType", "form",
                        "sourceField", "quantity"))));

        BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate));
    }

    @Test
    @DisplayName("rejects unsupported numeric ASSERT_RECORD operators")
    void rejectsUnsupportedNumericAssertRecordOperators() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "numericConstraints", List.of(Map.of(
                        "field", "pickedQuantity",
                        "operator", "between",
                        "value", 1))));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate)));
    }

    @Test
    @DisplayName("rejects protected fields in numeric ASSERT_RECORD constraints")
    void rejectsProtectedNumericAssertRecordFields() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "numericConstraints", List.of(Map.of(
                        "field", "tenantId",
                        "operator", "eq",
                        "value", 1))));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate)));
    }

    @Test
    @DisplayName("rejects non-numeric static ASSERT_RECORD values")
    void rejectsNonNumericStaticAssertRecordValues() {
        BusinessActionStepDTO gate = step("ASSERT_RECORD", Map.of(
                "numericConstraints", List.of(Map.of(
                        "field", "pickedQuantity",
                        "operator", "gte",
                        "value", "not-a-number"))));

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "LOCAL_TRANSACTION")), List.of(gate)));
    }

    @Test
    @DisplayName("accepts governed external API steps in orchestration mode")
    void acceptsGovernedCallApiStep() {
        BusinessActionStepDTO callApi = step("CALL_API", Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/deduct",
                "paramMappings", List.of(Map.of(
                        "param", "sku",
                        "sourceType", "record",
                        "sourceField", "skuCode")),
                "resultMappings", List.of(Map.of(
                        "from", "resultCode",
                        "to", "inventoryResult",
                        "target", "STEP_CONTEXT")),
                "failureStrategy", "THROW"));

        BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "ORCHESTRATION")), List.of(callApi));
    }

    @Test
    @DisplayName("rejects datasets arbitrary URLs and incoherent failure policy for CALL_API")
    void rejectsUnsafeCallApiDefinitions() {
        BusinessActionStepDTO dataset = step("CALL_API", Map.of(
                "sourceType", "DATASET",
                "sourceKey", "inventory_dataset"));
        BusinessActionStepDTO arbitraryUrl = step("CALL_API", Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/deduct",
                "url", "https://example.invalid"));
        BusinessActionStepDTO incoherentFailure = step("CALL_API", Map.of(
                "sourceType", "EXTERNAL_API",
                "sourceKey", "inventory/deduct",
                "failureStrategy", "LOG_AND_CONTINUE"));
        incoherentFailure.setRollbackOnFailure(true);

        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "ORCHESTRATION")), List.of(dataset)));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "ORCHESTRATION")), List.of(arbitraryUrl)));
        assertThrows(BusinessException.class, () -> BusinessActionCommandPolicy.validateDefinition(
                action(Map.of("executionMode", "ORCHESTRATION")), List.of(incoherentFailure)));
    }

    private BusinessActionStepDTO step(String type, Map<String, Object> config) {
        BusinessActionStepDTO step = new BusinessActionStepDTO();
        step.setStepType(type);
        step.setRollbackOnFailure(true);
        step.setStepConfig(config);
        return step;
    }

    private BusinessObjectActionVO action(Map<String, Object> config) {
        BusinessObjectActionVO action = new BusinessObjectActionVO();
        action.setActionCode("confirm");
        action.setActionName("确认");
        action.setActionConfig(config);
        return action;
    }
}
