package com.mdframe.forge.plugin.data.service;

import com.mdframe.forge.plugin.data.dto.DataDatasetQueryDTO;
import com.mdframe.forge.plugin.data.entity.DataConnection;
import com.mdframe.forge.plugin.data.entity.DataDataset;
import com.mdframe.forge.plugin.data.entity.DataDatasetField;
import com.mdframe.forge.plugin.data.enums.DataDatasetAccessLevelEnum;
import com.mdframe.forge.plugin.data.enums.DatasetPublishStatusEnum;
import com.mdframe.forge.plugin.data.support.DataDatasetFieldViewAssembler;
import com.mdframe.forge.plugin.data.vo.DataDatasetMetadataVO;
import com.mdframe.forge.plugin.data.vo.DataDatasetQueryResultVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DataDatasetRuntimeService {

    private final DataDatasetService datasetService;
    private final DataDatasetAccessService datasetAccessService;
    private final DataConnectionService connectionService;
    private final DataDatasetFieldService fieldService;
    private final DataQueryExecutor queryExecutor;
    private final DataDatasetFieldViewAssembler fieldViewAssembler;

    public List<DataDataset> listAvailable() {
        return datasetService.listByConnectionId(null);
    }

    public DataDatasetQueryResultVO query(DataDatasetQueryDTO dto) {
        if (dto == null || dto.getDatasetId() == null) {
            throw new BusinessException("数据集ID不能为空");
        }
        return execute(requireById(dto.getDatasetId()), dto);
    }

    public DataDatasetQueryResultVO queryByCode(String datasetCode, DataDatasetQueryDTO dto) {
        DataDataset dataset = requireByCode(datasetCode);
        DataDatasetQueryDTO resolved = copyQuery(dto);
        resolved.setDatasetId(dataset.getId());
        return execute(dataset, resolved);
    }

    public DataDatasetMetadataVO metadata(Long id) {
        if (id == null) {
            throw new BusinessException("数据集ID不能为空");
        }
        return buildMetadata(requireById(id));
    }

    public DataDatasetMetadataVO metadataByCode(String datasetCode) {
        return buildMetadata(requireByCode(datasetCode));
    }

    private DataDatasetQueryResultVO execute(DataDataset dataset, DataDatasetQueryDTO dto) {
        datasetAccessService.requireAccess(dataset, DataDatasetAccessLevelEnum.QUERY);
        DataConnection connection = requireEnabledConnection(dataset.getConnectionId());
        List<DataDatasetField> fields = fieldService.listByDatasetId(dataset.getId());
        return queryExecutor.execute(dataset, connection, fields, dto);
    }

    private DataDatasetMetadataVO buildMetadata(DataDataset dataset) {
        datasetAccessService.requireAccess(dataset, DataDatasetAccessLevelEnum.VIEW);
        List<DataDatasetField> fields = fieldService.listByDatasetId(dataset.getId());
        DataDatasetMetadataVO metadata = new DataDatasetMetadataVO();
        metadata.setDatasetId(dataset.getId());
        metadata.setDatasetCode(dataset.getDatasetCode());
        metadata.setDatasetName(dataset.getDatasetName());
        metadata.setDatasetType(dataset.getDatasetType());
        metadata.setFields(fieldViewAssembler.toVOList(fields));
        metadata.setParamSchemaJson(dataset.getParamSchemaJson());
        return metadata;
    }

    private DataDataset requireById(Long id) {
        DataDataset dataset = datasetService.getById(id);
        if (dataset == null) {
            throw new BusinessException("数据集不存在或已删除");
        }
        requireUsable(dataset);
        return dataset;
    }

    private DataDataset requireByCode(String datasetCode) {
        if (datasetCode == null || datasetCode.isBlank() || datasetCode.length() > 100) {
            throw new BusinessException("数据集编码格式不正确");
        }
        DataDataset dataset = datasetService.getByCode(datasetCode.trim());
        if (dataset == null) {
            throw new BusinessException("数据集不存在、未发布或已禁用");
        }
        requireUsable(dataset);
        return dataset;
    }

    private void requireUsable(DataDataset dataset) {
        if (!DatasetPublishStatusEnum.isPublished(dataset.getPublishStatus())) {
            throw new BusinessException("数据集未发布，暂不可使用");
        }
        if (!Integer.valueOf(1).equals(dataset.getStatus())) {
            throw new BusinessException("数据集已禁用");
        }
    }

    private DataConnection requireEnabledConnection(Long connectionId) {
        DataConnection connection = connectionService.getById(connectionId);
        if (connection == null) {
            throw new BusinessException("数据连接不存在或已删除");
        }
        if (!Integer.valueOf(1).equals(connection.getStatus())) {
            throw new BusinessException("数据连接已禁用");
        }
        return connection;
    }

    private DataDatasetQueryDTO copyQuery(DataDatasetQueryDTO source) {
        DataDatasetQueryDTO target = new DataDatasetQueryDTO();
        if (source == null) {
            return target;
        }
        target.setParams(source.getParams());
        target.setFields(source.getFields());
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
        target.setMaxRows(source.getMaxRows());
        target.setOutputMode(source.getOutputMode());
        return target;
    }
}
