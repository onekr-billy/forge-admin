package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessQuantityOperationDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessActionStepResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessQuantityOperationResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 通用数量台账领域动作。
 */
@Component
@RequiredArgsConstructor
public class BusinessQuantityDomainActionExecutor implements BusinessDomainActionExecutor {

    private final ObjectMapper objectMapper;
    private final BusinessQuantityLedgerService quantityLedgerService;

    @Override
    public String actionType() {
        return "QUANTITY";
    }

    @Override
    public BusinessActionStepResultVO execute(BusinessActionExecutionContext context, Map<String, Object> config) {
        BusinessQuantityOperationDTO dto = buildOperationDTO(context, config);
        BusinessQuantityOperationResultVO operationResult = quantityLedgerService.operate(dto);

        BusinessActionStepResultVO result = new BusinessActionStepResultVO();
        result.setStatus(com.mdframe.forge.plugin.generator.enums.BusinessActionStepStatus.SUCCESS.getCode());
        result.setMessage(StringUtils.defaultIfBlank(operationResult.getMessage(), "数量台账动作执行成功"));
        result.getResult().putAll(toMap(operationResult));
        return result;
    }

    private BusinessQuantityOperationDTO buildOperationDTO(BusinessActionExecutionContext context, Map<String, Object> config) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (config != null) {
            values.putAll(config);
            values.putAll(BusinessActionStepConfigHelper.asMap(config.get("params")));
        }

        BusinessQuantityOperationDTO dto = new BusinessQuantityOperationDTO();
        dto.setOperationType(firstText(values, "operationType", "operation", "quantityOperation"));
        if (StringUtils.isBlank(dto.getOperationType())) {
            throw new BusinessException("数量台账领域动作缺少 operationType");
        }
        dto.setAccountCode(firstText(values, "accountCode", "account"));
        dto.setItemCode(resolveItemCode(values, context));
        dto.setDimensionKey(firstText(values, "dimensionKey", "dimension"));
        dto.setQuantity(firstLong(values, "quantity", "qty", "amount"));
        dto.setTargetAccountCode(firstText(values, "targetAccountCode", "targetAccount"));
        dto.setTargetItemCode(firstText(values, "targetItemCode", "targetItem"));
        dto.setTargetDimensionKey(firstText(values, "targetDimensionKey", "targetDimension"));
        dto.setLockId(firstLong(values, "lockId"));
        dto.setLockCode(firstText(values, "lockCode"));
        dto.setSourceObjectCode(StringUtils.defaultIfBlank(
                firstText(values, "sourceObjectCode"),
                context.getRequest() == null ? null : context.getRequest().getObjectCode()));
        dto.setSourceRecordId(StringUtils.defaultIfBlank(
                firstText(values, "sourceRecordId"),
                context.getRequest() == null ? null : context.getRequest().getRecordId()));
        dto.setSourceDetailId(firstText(values, "sourceDetailId", "detailId"));
        dto.setCorrelationId(StringUtils.defaultIfBlank(firstText(values, "correlationId"), context.getCorrelationId()));
        dto.setIdempotencyKey(resolveIdempotencyKey(context, values, dto));
        dto.setRemark(firstText(values, "remark"));
        dto.setExtraData(buildExtraData(values));
        return dto;
    }

    private String resolveItemCode(Map<String, Object> values, BusinessActionExecutionContext context) {
        String configured = firstText(values, "itemCode", "item");
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        String scoped = firstConfiguredFallbackText(values, context);
        if (StringUtils.isNotBlank(scoped)) {
            return scoped;
        }
        String sourceDetailId = firstText(values, "sourceDetailId", "detailId");
        throw new BusinessException("数量项编码不能为空"
                + (StringUtils.isBlank(sourceDetailId) ? "" : ": sourceDetailId=" + sourceDetailId));
    }

    private String firstConfiguredFallbackText(Map<String, Object> values, BusinessActionExecutionContext context) {
        for (Object item : BusinessActionStepConfigHelper.firstList(values,
                "itemCodeFallbackFields", "itemFallbackFields", "itemCodeFallbackPaths")) {
            String path = item == null ? null : StringUtils.trimToNull(String.valueOf(item));
            if (path == null) {
                continue;
            }
            Object value = BusinessActionStepConfigHelper.resolvePath(path, context);
            if (value == null && !path.contains(".")) {
                value = firstScopedFieldValue(context, path);
            }
            String text = value == null ? null : StringUtils.trimToNull(String.valueOf(value));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private String resolveIdempotencyKey(BusinessActionExecutionContext context,
                                         Map<String, Object> values,
                                         BusinessQuantityOperationDTO dto) {
        String configured = firstText(values, "idempotencyKey", "idempotentKey");
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        String stepCode = firstText(values, "stepCode");
        String base = context.getRequest() == null ? null : context.getRequest().getIdempotencyKey();
        if (StringUtils.isBlank(base)) {
            base = buildStableIdempotencyBase(context, dto, stepCode);
        }
        String raw = StringUtils.defaultString(base)
                + ":quantity:"
                + StringUtils.defaultString(stepCode, "step")
                + ":"
                + StringUtils.defaultString(dto.getOperationType()).toLowerCase(Locale.ROOT);
        int maxLength = "TRANSFER".equalsIgnoreCase(dto.getOperationType()) ? 121 : 128;
        return compactIdempotencyKey(raw, maxLength);
    }

    private String buildStableIdempotencyBase(BusinessActionExecutionContext context,
                                              BusinessQuantityOperationDTO dto,
                                              String stepCode) {
        String sourceObjectCode = StringUtils.firstNonBlank(dto.getSourceObjectCode(),
                context.getRequest() == null ? null : context.getRequest().getObjectCode());
        String sourceRecordId = StringUtils.firstNonBlank(dto.getSourceRecordId(),
                context.getRequest() == null ? null : context.getRequest().getRecordId());
        if (StringUtils.isAnyBlank(sourceObjectCode, sourceRecordId)) {
            throw new BusinessException("数量台账领域动作缺少稳定幂等来源，请配置 idempotencyKey 或来源对象和记录");
        }
        String actionCode = context.getAction() == null ? null : context.getAction().getActionCode();
        String raw = String.join("|",
                "quantity",
                StringUtils.defaultString(sourceObjectCode),
                StringUtils.defaultString(sourceRecordId),
                StringUtils.defaultString(dto.getSourceDetailId()),
                StringUtils.defaultString(actionCode),
                StringUtils.defaultString(stepCode, "step"),
                StringUtils.defaultString(dto.getOperationType()),
                StringUtils.defaultString(dto.getAccountCode()),
                StringUtils.defaultString(dto.getItemCode()),
                StringUtils.defaultString(dto.getDimensionKey()),
                StringUtils.defaultString(dto.getTargetAccountCode()),
                StringUtils.defaultString(dto.getTargetItemCode()),
                StringUtils.defaultString(dto.getTargetDimensionKey()));
        return "quantity:" + sha256(raw);
    }

    private String compactIdempotencyKey(String raw, int maxLength) {
        if (raw.length() <= maxLength) {
            return raw;
        }
        int prefixLength = Math.max(16, maxLength - 33);
        return StringUtils.left(raw, prefixLength) + ":" + sha256(raw).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BusinessException("数量台账幂等键生成失败");
        }
    }

    private Map<String, Object> buildExtraData(Map<String, Object> values) {
        Map<String, Object> extraData = new LinkedHashMap<>();
        Object raw = values.get("extraData");
        if (raw instanceof Map<?, ?>) {
            extraData.putAll(BusinessActionStepConfigHelper.asMap(raw));
        }
        return extraData;
    }

    private String firstText(Map<String, Object> values, String... keys) {
        Object value = BusinessActionStepConfigHelper.firstValue(values, keys);
        return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
    }

    private Object firstScopedFieldValue(BusinessActionExecutionContext context, String field) {
        if (context == null || context.getScopedVariables() == null || context.getScopedVariables().isEmpty()) {
            return null;
        }
        for (Object scopedValue : context.getScopedVariables().values()) {
            Map<String, Object> scopedMap = BusinessActionStepConfigHelper.asMap(scopedValue);
            if (scopedMap.isEmpty()) {
                continue;
            }
            Object value = BusinessActionStepConfigHelper.readPath(scopedMap, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long firstLong(Map<String, Object> values, String... keys) {
        Object value = BusinessActionStepConfigHelper.firstValue(values, keys);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return parseWholeNumber(number);
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).toBigIntegerExact().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BusinessException("数量字段必须是整数最小单位: " + value);
        }
    }

    private Long parseWholeNumber(Number number) {
        if (number instanceof Float || number instanceof Double || number instanceof BigDecimal) {
            try {
                return new BigDecimal(String.valueOf(number)).toBigIntegerExact().longValueExact();
            } catch (ArithmeticException e) {
                throw new BusinessException("数量字段必须是整数最小单位: " + number);
            }
        }
        return number.longValue();
    }

    private Map<String, Object> toMap(BusinessQuantityOperationResultVO result) {
        if (result == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(result, new TypeReference<>() {
        });
    }
}
