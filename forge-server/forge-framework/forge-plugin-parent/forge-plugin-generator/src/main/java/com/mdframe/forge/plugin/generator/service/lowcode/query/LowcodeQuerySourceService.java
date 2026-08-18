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
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceExecuteDTO;
import com.mdframe.forge.plugin.generator.dto.lowcode.query.LowcodeQuerySourceRefDTO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceCatalogVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceFieldVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceMetadataVO;
import com.mdframe.forge.plugin.generator.vo.lowcode.query.LowcodeQuerySourceResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ExternalQuerySourceService externalQuerySourceService;
    private final DataDatasetRuntimeService datasetRuntimeService;
    private final ObjectMapper objectMapper;

    public List<LowcodeQuerySourceCatalogVO> catalog(String keyword) {
        List<LowcodeQuerySourceCatalogVO> sources = new ArrayList<>();
        externalQuerySourceService.listAvailable().forEach(api -> sources.add(externalCatalog(api)));
        datasetRuntimeService.listAvailable().forEach(dataset -> sources.add(datasetCatalog(dataset)));
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
        DataDatasetMetadataVO metadata = datasetRuntimeService.metadataByCode(sourceKey);
        return datasetMetadata(metadata);
    }

    public LowcodeQuerySourceResultVO execute(LowcodeQuerySourceExecuteDTO dto) {
        String sourceType = requireSourceType(dto);
        String sourceKey = requireSourceKey(dto);
        long startedAt = System.currentTimeMillis();
        if (EXTERNAL_API.equals(sourceType)) {
            ExternalApi metadata = externalQuerySourceService.requireMetadata(sourceKey);
            Object data = externalQuerySourceService.execute(sourceKey, dto.getParams());
            logResult(sourceType, metadata.getId(), data, startedAt);
            return LowcodeQuerySourceResultVO.builder()
                    .sourceType(sourceType)
                    .sourceKey(sourceKey)
                    .sourceId(metadata.getId())
                    .data(data)
                    .fields(externalFields(metadata.getOutputSchemaJson()))
                    .build();
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
                        .sensitive(!"PUBLIC".equalsIgnoreCase(defaultText(field.getSensitiveLevel(), "PUBLIC")))
                        .build())
                .toList();
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
        if (!EXTERNAL_API.equals(sourceType) && !DATASET.equals(sourceType)) {
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
