package com.mdframe.forge.plugin.data.support;

import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import com.mdframe.forge.plugin.data.entity.DataDimension;
import com.mdframe.forge.plugin.data.mapper.DataDimensionMapper;
import com.mdframe.forge.plugin.data.vo.DataDatasetFieldVO;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataDatasetFieldViewAssembler {

    private final DataDimensionMapper dimensionMapper;

    public List<DataDatasetFieldVO> toVOList(List<DataDatasetField> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, DataDimension> dimensionMap = loadDimensionMap(fields);
        return fields.stream()
            .map(field -> toVO(field, dimensionMap))
            .collect(Collectors.toList());
    }

    private DataDatasetFieldVO toVO(DataDatasetField field, Map<Long, DataDimension> dimensionMap) {
        DataDatasetFieldVO vo = new DataDatasetFieldVO();
        vo.setId(field.getId());
        vo.setFieldName(field.getFieldName());
        vo.setFieldLabel(field.getFieldLabel());
        vo.setSourceColumn(field.getSourceColumn());
        vo.setDbType(field.getDbType());
        vo.setDataType(field.getDataType());
        vo.setFieldRole(field.getFieldRole());
        vo.setDefaultAgg(field.getDefaultAgg());
        vo.setQueryEnabled(field.getQueryEnabled());
        vo.setDisplayEnabled(field.getDisplayEnabled());
        vo.setSensitiveLevel(field.getSensitiveLevel());
        vo.setMaskRule(field.getMaskRule());
        vo.setDictType(field.getDictType());
        vo.setDateFormat(field.getDateFormat());
        vo.setDataUnit(field.getDataUnit());
        vo.setDimensionId(field.getDimensionId());
        vo.setSort(field.getSort());
        vo.setDescription(field.getDescription());

        DataDimension dimension = dimensionMap.get(field.getDimensionId());
        if (dimension != null) {
            vo.setDimensionCode(dimension.getDimensionCode());
            vo.setDimensionName(dimension.getDimensionName());
        }
        return vo;
    }

    private Map<Long, DataDimension> loadDimensionMap(List<DataDatasetField> fields) {
        Set<Long> dimensionIds = fields.stream()
            .map(DataDatasetField::getDimensionId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (dimensionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return dimensionMapper.selectDimensionByIds(SessionHelper.getTenantId(), dimensionIds).stream()
            .collect(Collectors.toMap(
                DataDimension::getId,
                dimension -> dimension,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }
}
