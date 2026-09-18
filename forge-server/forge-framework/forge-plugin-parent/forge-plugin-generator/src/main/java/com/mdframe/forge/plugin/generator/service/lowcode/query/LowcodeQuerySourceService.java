package com.mdframe.forge.plugin.generator.service.lowcode.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.service.DataDatasetRuntimeService;
import com.mdframe.forge.plugin.data.vo.DataDatasetFieldVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetMetadataVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.service.ExternalQuerySourceService;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceRefDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessRecordSelectorService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessRecordSelectorResultVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceCatalogVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceFieldVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceMetadataVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LowcodeQuerySourceService {

    public static final String EXTERNAL_API = "EXTERNAL_API";
    public static final String DATASET = "DATASET";
    public static final String BUSINESS_OBJECT = "BUSINESS_OBJECT";

    private final ExternalQuerySourceService externalQuerySourceService;
    private final DataDatasetRuntimeService datasetRuntimeService;
    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessRecordSelectorService businessRecordSelectorService;
    private final ObjectMapper objectMapper;

    public List<LowcodeQuerySourceCatalogVO> catalog(String keyword) {
        List<LowcodeQuerySourceCatalogVO> sources = new ArrayList<>();
        externalQuerySourceService.listAvailable().forEach(api -> sources.add(externalCatalog(api)));
        datasetRuntimeService.listAvailable().forEach(dataset -> sources.add(datasetCatalog(dataset)));
        publishedBusinessObjects().forEach(object -> sources.add(businessObjectCatalog(object)));
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return sources.stream()
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .sorted(Comparator.comparing(LowcodeQuerySourceCatalogVO::getSourceType)
                        .thenComparing(LowcodeQuerySourceCatalogVO::getSourceName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(1000)
                .toList();
    }

    public LowcodeQuerySourceMetadataVO metadata(LowcodeQuerySourceRefDTO ref) {
        String sourceType = requireSourceType(ref);
        String sourceKey = requireSourceKey(ref);
        if (EXTERNAL_API.equals(sourceType)) {
            return externalMetadata(externalQuerySourceService.requireMetadata(sourceKey));
        }
        if (BUSINESS_OBJECT.equals(sourceType)) {
            return businessObjectMetadata(sourceKey);
        }
        DataDatasetMetadataVO metadata = datasetRuntimeService.metadataByCode(sourceKey);
        return datasetMetadata(metadata);
    }

    public LowcodeQuerySourceResultVO execute(LowcodeQuerySourceExecuteDTO dto) {
        String sourceType = requireSourceType(dto);
        String sourceKey = requireSourceKey(dto);
        long startedAt = System.currentTimeMillis();
        if (EXTERNAL_API.equals(sourceType)) {
            ExternalApi metadata = externalQuerySourceService.requireMetadata(sourceKey);
            Object data = normalizeExternalData(externalQuerySourceService.execute(sourceKey, dto.getParams()));
            logResult(sourceType, metadata.getId(), data, startedAt);
            return LowcodeQuerySourceResultVO.builder()
                    .sourceType(sourceType)
                    .sourceKey(sourceKey)
                    .sourceId(metadata.getId())
                    .data(data)
                    .fields(externalFields(metadata.getOutputSchemaJson()))
                    .build();
        }
        if (BUSINESS_OBJECT.equals(sourceType)) {
            return executeBusinessObject(sourceKey, dto, startedAt);
        }

        DataDatasetMetadataVO metadata = datasetRuntimeService.metadataByCode(sourceKey);
        DataDatasetQueryDTO query = datasetQuery(dto);
        DataDatasetQueryResultVO result = datasetRuntimeService.queryByCode(sourceKey, query);
        logResult(sourceType, metadata.getDatasetId(), result.getSource(), startedAt);
        return LowcodeQuerySourceResultVO.builder()
                .sourceType(sourceType)
                .sourceKey(sourceKey)
                .sourceId(metadata.getDatasetId())
                .data(result.getSource())
                .total(result.getTotal())
                .pageNum(result.getPageNum())
                .pageSize(result.getPageSize())
                .fields(datasetFields(result.getFields()))
                .build();
    }

    /**
     * 外部接口返回统一规范为数组：代理层已按 responseDataPath 提取数据，
     * 若提取结果为单个对象则包装为单元素列表，保证前端始终收到数组。
     */
    private Object normalizeExternalData(Object data) {
        if (data == null) {
            return List.of();
        }
        if (data instanceof List) {
            return data;
        }
        return List.of(data);
    }

    private LowcodeQuerySourceCatalogVO externalCatalog(ExternalApi api) {
        return LowcodeQuerySourceCatalogVO.builder()
                .sourceType(EXTERNAL_API)
                .sourceKey(externalQuerySourceService.sourceKey(api))
                .sourceId(api.getId())
                .sourceName(api.getApiName())
                .sourceGroup(api.getSystemName())
                .description(api.getApiDesc())
                .build();
    }

    private LowcodeQuerySourceCatalogVO datasetCatalog(DataDataset dataset) {
        return LowcodeQuerySourceCatalogVO.builder()
                .sourceType(DATASET)
                .sourceKey(dataset.getDatasetCode())
                .sourceId(dataset.getId())
                .sourceName(dataset.getDatasetName())
                .sourceGroup(dataset.getCategoryName())
                .description(dataset.getDescription())
                .build();
    }

    private LowcodeQuerySourceCatalogVO businessObjectCatalog(AiBusinessObject object) {
        return LowcodeQuerySourceCatalogVO.builder()
                .sourceType(BUSINESS_OBJECT)
                .sourceKey(object.getObjectCode())
                .sourceId(object.getId())
                .sourceName(object.getObjectName())
                .sourceGroup("业务对象")
                .description(object.getDescription())
                .build();
    }

    private List<AiBusinessObject> publishedBusinessObjects() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception exception) {
            return List.of();
        }
        if (tenantId == null) {
            return List.of();
        }
        return businessObjectMapper.selectPublishedObjects(tenantId);
    }

    private LowcodeQuerySourceMetadataVO businessObjectMetadata(String objectCode) {
        AiBusinessObject object = businessRecordSelectorService.requireObjectByCode(objectCode);
        if (StringUtils.isBlank(object.getConfigKey())) {
            throw new BusinessException("业务对象未发布运行配置: " + object.getObjectCode());
        }
        Map<String, String> labels = businessRecordSelectorService.fieldLabels(object);
        return LowcodeQuerySourceMetadataVO.builder()
                .sourceType(BUSINESS_OBJECT)
                .sourceKey(object.getObjectCode())
                .sourceId(object.getId())
                .sourceName(object.getObjectName())
                .fields(businessObjectFields(labels))
                .build();
    }

    private List<LowcodeQuerySourceFieldVO> businessObjectFields(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        return labels.entrySet().stream()
                .map(entry -> LowcodeQuerySourceFieldVO.builder()
                        .field(entry.getKey())
                        .label(StringUtils.defaultIfBlank(entry.getValue(), entry.getKey()))
                        .type("string")
                        .path(entry.getKey())
                        .sensitive(false)
                        .build())
                .toList();
    }

    private LowcodeQuerySourceResultVO executeBusinessObject(String sourceKey,
                                                              LowcodeQuerySourceExecuteDTO dto,
                                                              long startedAt) {
        BusinessRecordSelectorResultVO result = businessRecordSelectorService.queryByObjectCode(
                sourceKey, dto.getParams(), dto.getPageNum(), dto.getPageSize());
        logResult(BUSINESS_OBJECT, null, result.getRecords(), startedAt);
        return LowcodeQuerySourceResultVO.builder()
                .sourceType(BUSINESS_OBJECT)
                .sourceKey(sourceKey)
                .data(result.getRecords())
                .total(result.getTotal())
                .pageNum(toInt(result.getCurrent()))
                .pageSize(toInt(result.getSize()))
                .fields(selectorColumnsToFields(result.getColumns()))
                .build();
    }

    private Integer toInt(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private List<LowcodeQuerySourceFieldVO> selectorColumnsToFields(List<BusinessRecordSelectorResultVO.SelectorColumnVO> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        return columns.stream()
                .map(column -> LowcodeQuerySourceFieldVO.builder()
                        .field(column.getField())
                        .label(StringUtils.defaultIfBlank(column.getLabel(), column.getField()))
                        .type(StringUtils.defaultIfBlank(column.getType(), "string"))
                        .path(column.getField())
                        .sensitive(false)
                        .build())
                .toList();
    }

    private LowcodeQuerySourceMetadataVO externalMetadata(ExternalApi api) {
        String sourceKey = externalQuerySourceService.sourceKey(api);
        return LowcodeQuerySourceMetadataVO.builder()
                .sourceType(EXTERNAL_API)
                .sourceKey(sourceKey)
                .sourceId(api.getId())
                .sourceName(api.getApiName())
                .inputSchemaJson(api.getInputSchemaJson())
                .fields(externalFields(api.getOutputSchemaJson()))
                .build();
    }

    private LowcodeQuerySourceMetadataVO datasetMetadata(DataDatasetMetadataVO metadata) {
        return LowcodeQuerySourceMetadataVO.builder()
                .sourceType(DATASET)
                .sourceKey(metadata.getDatasetCode())
                .sourceId(metadata.getDatasetId())
                .sourceName(metadata.getDatasetName())
                .inputSchemaJson(metadata.getParamSchemaJson())
                .fields(datasetFields(metadata.getFields()))
                .build();
    }

    private List<LowcodeQuerySourceFieldVO> externalFields(String outputSchemaJson) {
        try {
            List<Map<String, Object>> definitions = objectMapper.readValue(
                    outputSchemaJson, new TypeReference<List<Map<String, Object>>>() {
                    });
            if (definitions == null) {
                return List.of();
            }
            return definitions.stream()
                    .map(item -> LowcodeQuerySourceFieldVO.builder()
                            .field(text(item.get("name")))
                            .label(defaultText(text(item.get("label")), text(item.get("name"))))
                            .type(defaultText(text(item.get("type")), "string"))
                            .path(defaultText(text(item.get("path")), text(item.get("name"))))
                            .sensitive(false)
                            .build())
                    .toList();
        } catch (Exception exception) {
            throw new BusinessException("外部查询源输出Schema不可用，请联系管理员修正配置");
        }
    }

    private List<LowcodeQuerySourceFieldVO> datasetFields(List<DataDatasetFieldVO> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream()
                .map(field -> LowcodeQuerySourceFieldVO.builder()
                        .field(field.getFieldName())
                        .label(defaultText(field.getFieldLabel(), field.getFieldName()))
                        .type(defaultText(field.getDataType(), field.getDbType()))
                        .path(field.getFieldName())
                        // 数据集敏感级别字典值：NONE=不脱敏、MASK=脱敏展示、HIDDEN=隐藏字段
                        // 只有 MASK / HIDDEN 才视为敏感，NONE（默认）和空值均不敏感
                        .sensitive(isSensitiveLevel(field.getSensitiveLevel()))
                        .build())
                .toList();
    }

    private static boolean isSensitiveLevel(String level) {
        if (StringUtils.isBlank(level) || "NONE".equalsIgnoreCase(level)) {
            return false;
        }
        return "MASK".equalsIgnoreCase(level) || "HIDDEN".equalsIgnoreCase(level);
    }

    private DataDatasetQueryDTO datasetQuery(LowcodeQuerySourceExecuteDTO source) {
        DataDatasetQueryDTO query = new DataDatasetQueryDTO();
        query.setParams(source.getParams());
        query.setFields(source.getFields());
        query.setPageNum(source.getPageNum());
        query.setPageSize(source.getPageSize());
        query.setMaxRows(source.getMaxRows());
        return query;
    }

    private boolean matchesKeyword(LowcodeQuerySourceCatalogVO item, String keyword) {
        return keyword.isEmpty()
                || contains(item.getSourceKey(), keyword)
                || contains(item.getSourceName(), keyword)
                || contains(item.getSourceGroup(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String requireSourceType(LowcodeQuerySourceRefDTO ref) {
        if (ref == null || ref.getSourceType() == null) {
            throw new BusinessException("低代码查询源类型不能为空");
        }
        String sourceType = ref.getSourceType().trim().toUpperCase(Locale.ROOT);
        if (!EXTERNAL_API.equals(sourceType) && !DATASET.equals(sourceType) && !BUSINESS_OBJECT.equals(sourceType)) {
            throw new BusinessException("不支持的低代码查询源类型");
        }
        return sourceType;
    }

    private String requireSourceKey(LowcodeQuerySourceRefDTO ref) {
        if (ref.getSourceKey() == null || ref.getSourceKey().isBlank() || ref.getSourceKey().length() > 129) {
            throw new BusinessException("低代码查询源编码不能为空且长度不能超过129");
        }
        return ref.getSourceKey().trim();
    }

    private void logResult(String sourceType, Long sourceId, Object result, long startedAt) {
        int resultSize = result instanceof Collection<?> collection ? collection.size() : result == null ? 0 : 1;
        log.info("低代码只读查询完成: sourceType={}, sourceId={}, resultSize={}, durationMs={}",
                sourceType, sourceId, resultSize, System.currentTimeMillis() - startedAt);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
