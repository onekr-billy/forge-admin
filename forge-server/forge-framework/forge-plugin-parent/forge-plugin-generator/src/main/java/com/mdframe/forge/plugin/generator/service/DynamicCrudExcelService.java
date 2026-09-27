package com.mdframe.forge.plugin.generator.service;

import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudExportTask;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudExportResult;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudImportResult;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.enums.DynamicCrudExportStatus;
import com.mdframe.forge.plugin.generator.mapper.AiCrudExportTaskMapper;
import com.mdframe.forge.plugin.generator.service.excel.DynamicCrudExcelColumn;
import com.mdframe.forge.plugin.generator.service.excel.DynamicCrudExcelRow;
import com.mdframe.forge.plugin.generator.service.excel.DynamicCrudExcelValueAdapter;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.datascope.context.DataScopeContext;
import com.mdframe.forge.starter.datascope.service.IDataScopeService;
import com.mdframe.forge.starter.excel.core.ExcelImportTemplateWriter;
import com.mdframe.forge.starter.excel.model.ExcelColumnConfig;
import com.mdframe.forge.starter.excel.spi.ExcelConfigProvider;
import com.mdframe.forge.starter.file.core.FileManager;
import com.mdframe.forge.starter.file.model.FileMetadata;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态 CRUD Excel 导入导出服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicCrudExcelService {

    private static final int MAX_IMPORT_ROWS = 5000;
    private static final int DEFAULT_ASYNC_THRESHOLD = 5000;
    private static final int DEFAULT_EXPORT_BATCH_SIZE = 1000;
    private static final int DEFAULT_FILE_KEEP_HOURS = 24;
    private static final String EXPORT_BUSINESS_TYPE = "ai_crud_export";
    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CONFIG_ASYNC_THRESHOLD = "sys.export.async.threshold";
    private static final String CONFIG_BATCH_SIZE = "sys.export.batch.size";
    private static final String CONFIG_FILE_KEEP_HOURS = "sys.export.file.keepHours";
    private final DynamicCrudService dynamicCrudService;
    private final ObjectMapper objectMapper;
    private final DynamicCrudExcelValueAdapter excelValueAdapter;
    private final ObjectProvider<ExcelConfigProvider> excelConfigProvider;
    private final ObjectProvider<DynamicCrudAsyncExportWorker> asyncExportWorker;
    private final AiCrudExportTaskMapper exportTaskMapper;
    private final FileManager fileManager;
    private final IDataScopeService dataScopeService;

    public DynamicCrudExportResult exportExcel(String configKey, DynamicCrudQuery query, HttpServletResponse response) {
        AiCrudConfig config = dynamicCrudService.getRuntimeConfig(configKey);
        List<DynamicCrudExcelColumn> columns = resolveExportColumns(config);
        if (columns.isEmpty()) {
            throw new BusinessException("没有可导出的字段");
        }

        int threshold = readPositiveIntConfig(CONFIG_ASYNC_THRESHOLD, DEFAULT_ASYNC_THRESHOLD, 1, 1_000_000);
        DataScopeContext dataScopeContext = captureDataScopeContext();
        long totalCount = dynamicCrudService.countExportRows(configKey, query, dataScopeContext);
        if (totalCount > threshold) {
            int batchSize = readPositiveIntConfig(CONFIG_BATCH_SIZE, DEFAULT_EXPORT_BATCH_SIZE, 100, 5000);
            int keepHours = readPositiveIntConfig(CONFIG_FILE_KEEP_HOURS, DEFAULT_FILE_KEEP_HOURS, 1, 720);
            AiCrudExportTask task = createExportTask(config, query, totalCount, keepHours);
            ExportExecutionContext context = new ExportExecutionContext(
                    task.getId(), resolveTenantId(), SessionHelper.getUserId(), SessionHelper.getMainOrgId(),
                    dataScopeContext, totalCount, batchSize, keepHours);
            asyncExportWorker.getObject().executeAsync(task.getId(), configKey, query, context);
            return DynamicCrudExportResult.async(task.getId(), totalCount, threshold);
        }

        int pageSize = (int) Math.max(1L, totalCount);
        List<Map<String, Object>> rows = dynamicCrudService.selectExportPageRows(configKey, query, 1, pageSize, dataScopeContext);
        List<List<String>> headers = buildHeaders(columns);
        List<List<Object>> data = rows.stream()
                .map(row -> excelValueAdapter.buildExportRow(row, columns))
                .toList();

        writeWorkbook(response, buildFileName(config, "导出数据"), "数据", headers, data);
        return DynamicCrudExportResult.sync(totalCount, threshold);
    }

    public void downloadImportTemplate(String configKey, HttpServletResponse response) {
        AiCrudConfig config = dynamicCrudService.getRuntimeConfig(configKey);
        List<DynamicCrudExcelColumn> columns = resolveImportColumns(config);
        if (columns.isEmpty()) {
            throw new BusinessException("没有可导入的字段");
        }
        writeImportTemplateWorkbook(response, buildFileName(config, "导入模板"), columns);
    }

    public Page<AiCrudExportTask> selectExportTaskPage(String configKey, PageQuery pageQuery) {
        return exportTaskMapper.selectTaskPage(
                pageQuery.toPage(),
                resolveTenantId(),
                SessionHelper.getUserId(),
                configKey
        );
    }

    public AiCrudExportTask selectExportTask(String configKey, Long taskId) {
        AiCrudExportTask task = exportTaskMapper.selectTaskById(resolveTenantId(), SessionHelper.getUserId(), taskId);
        if (task == null || !StringUtils.equals(task.getConfigKey(), configKey)) {
            throw new BusinessException("导出任务不存在");
        }
        return task;
    }

    void executeAsyncExportTask(Long taskId,
                                String configKey,
                                DynamicCrudQuery query,
                                ExportExecutionContext context) {
        TenantContextHolder.executeWithTenant(context.tenantId(), () ->
                doExecuteAsyncExportTask(taskId, configKey, query, context));
    }

    private void doExecuteAsyncExportTask(Long taskId,
                                          String configKey,
                                          DynamicCrudQuery query,
                                          ExportExecutionContext context) {
        AiCrudExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[DynamicCrudExcelService] 异步导出任务不存在, taskId={}", taskId);
            return;
        }

        Path tempFile = null;
        try {
            markTaskRunning(taskId);
            AiCrudConfig config = dynamicCrudService.getRuntimeConfig(configKey);
            List<DynamicCrudExcelColumn> columns = resolveExportColumns(config);
            if (columns.isEmpty()) {
                throw new BusinessException("没有可导出的字段");
            }

            tempFile = Files.createTempFile("ai-crud-export-" + taskId + "-", ".xlsx");
            writeAsyncWorkbook(tempFile, configKey, query, columns, context);

            FileMetadata metadata;
            long fileSize = Files.size(tempFile);
            try (InputStream inputStream = Files.newInputStream(tempFile)) {
                metadata = fileManager.upload(inputStream, task.getFileName(), XLSX_MIME,
                        EXPORT_BUSINESS_TYPE, String.valueOf(taskId), null, true, fileSize);
            }

            AiCrudExportTask success = new AiCrudExportTask();
            success.setId(taskId);
            success.setStatus(DynamicCrudExportStatus.SUCCESS.getCode());
            success.setProgress(100);
            success.setExportedCount(context.totalCount());
            success.setFileId(metadata.getFileId());
            success.setFileSize(metadata.getFileSize());
            success.setFinishTime(LocalDateTime.now());
            exportTaskMapper.updateById(success);
            log.info("[DynamicCrudExcelService] 异步导出完成, taskId={}, fileId={}, total={}",
                    taskId, metadata.getFileId(), context.totalCount());
        } catch (Exception e) {
            log.error("[DynamicCrudExcelService] 异步导出失败, taskId={}", taskId, e);
            AiCrudExportTask failed = new AiCrudExportTask();
            failed.setId(taskId);
            failed.setStatus(DynamicCrudExportStatus.FAILED.getCode());
            failed.setErrorMessage(StringUtils.left(e.getMessage(), 1000));
            failed.setFinishTime(LocalDateTime.now());
            exportTaskMapper.updateById(failed);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("[DynamicCrudExcelService] 删除导出临时文件失败, path={}", tempFile, e);
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public DynamicCrudImportResult importExcel(String configKey, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("导入文件不能为空");
        }

        AiCrudConfig config = dynamicCrudService.getRuntimeConfig(configKey);
        List<DynamicCrudExcelColumn> columns = resolveImportColumns(config);
        if (columns.isEmpty()) {
            throw new BusinessException("没有可导入的字段");
        }

        ExcelReadResult readResult = readExcel(file);
        DynamicCrudImportResult result = new DynamicCrudImportResult();
        result.setTotalRows(readResult.getRows().size());

        if (readResult.getRows().size() > MAX_IMPORT_ROWS) {
            result.addError(1, null, null, readResult.getRows().size(), "单次最多导入" + MAX_IMPORT_ROWS + "行");
            return finishFailure(result);
        }

        Map<Integer, DynamicCrudExcelColumn> headerMapping = buildHeaderMapping(readResult, columns, result);
        if (!result.getErrors().isEmpty()) {
            return finishFailure(result);
        }

        List<Map<String, Object>> importRows = excelValueAdapter.buildImportRows(
                readResult.getRows(), headerMapping, result);
        if (!result.getErrors().isEmpty()) {
            return finishFailure(result);
        }

        try (AutoCloseable ignored = com.mdframe.forge.plugin.generator.service.audit.DataAuditTransactionHolder
                .overrideSource(com.mdframe.forge.plugin.generator.enums.DataAuditSourceType.IMPORT)) {
            for (Map<String, Object> row : importRows) {
                dynamicCrudService.insert(configKey, row);
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ex.getMessage(), ex);
        }

        result.setSuccess(true);
        result.setSuccessRows(importRows.size());
        result.setFailedRows(0);
        result.setSummary("导入成功，共" + importRows.size() + "行");
        return result;
    }

    private ExcelReadResult readExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MapRowReadListener listener = new MapRowReadListener();
            EasyExcel.read(inputStream, listener).sheet().doRead();
            ExcelReadResult result = new ExcelReadResult();
            result.setHeaders(listener.getHeaders());
            result.setRows(listener.getRows());
            return result;
        } catch (IOException e) {
            throw new BusinessException("读取导入文件失败: " + e.getMessage());
        }
    }

    private Map<Integer, DynamicCrudExcelColumn> buildHeaderMapping(ExcelReadResult readResult,
                                                                    List<DynamicCrudExcelColumn> columns,
                                                                    DynamicCrudImportResult result) {
        Map<String, DynamicCrudExcelColumn> knownHeaders = new HashMap<>();
        for (DynamicCrudExcelColumn column : columns) {
            knownHeaders.put(excelValueAdapter.normalizeHeader(column.getLabel()), column);
            knownHeaders.put(excelValueAdapter.normalizeHeader(column.getField()), column);
        }

        Map<Integer, DynamicCrudExcelColumn> mapping = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : readResult.getHeaders().entrySet()) {
            DynamicCrudExcelColumn column = knownHeaders.get(excelValueAdapter.normalizeHeader(entry.getValue()));
            if (column != null) {
                mapping.put(entry.getKey(), column);
            }
        }

        if (mapping.isEmpty()) {
            result.addError(1, null, null, null, "导入模板不匹配，未识别到可导入字段");
            return mapping;
        }

        Set<String> presentFields = mapping.values().stream()
                .map(DynamicCrudExcelColumn::getField)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (DynamicCrudExcelColumn column : columns) {
            if (column.isRequired() && !presentFields.contains(column.getField())) {
                result.addError(1, column.getField(), column.getLabel(), null,
                        "导入模板缺少必填列: " + column.getLabel());
            }
        }
        return mapping;
    }

    private DynamicCrudImportResult finishFailure(DynamicCrudImportResult result) {
        result.setSuccess(false);
        result.setSuccessRows(0);
        long failedRows = result.getErrors().stream()
                .map(error -> error.getRowNum() == null ? 0 : error.getRowNum())
                .distinct()
                .count();
        result.setFailedRows((int) failedRows);
        result.setSummary("导入校验失败，共" + result.getErrors().size() + "个错误");
        return result;
    }

    private List<DynamicCrudExcelColumn> resolveExportColumns(AiCrudConfig config) {
        JsonNode schema = readArray(config.getColumnsSchema(), "columnsSchema");
        Map<String, TransMeta> transConfig = readTransConfig(config.getTransConfig());
        Map<String, String> modelDataTypes = readModelDataTypes(config.getModelSchema());

        List<DynamicCrudExcelColumn> columns = new ArrayList<>();
        for (JsonNode node : schema) {
            String field = getFirstText(node, "prop", "dataIndex", "key", "field");
            if (StringUtils.isBlank(field) || isActionField(field)) {
                continue;
            }
            DynamicCrudExcelColumn meta = new DynamicCrudExcelColumn();
            meta.setField(field);
            meta.setLabel(StringUtils.defaultIfBlank(getFirstText(node, "title", "label", "columnName"), field));
            meta.setDataType(modelDataTypes.get(field));
            meta.setDictType(resolveDictType(field, node, transConfig));
            meta.setTargetField(resolveTargetField(field, node, transConfig));
            columns.add(meta);
        }
        return applyExportColumnConfig(config, columns);
    }

    private List<DynamicCrudExcelColumn> resolveImportColumns(AiCrudConfig config) {
        JsonNode schema = readArray(config.getEditSchema(), "editSchema");
        Map<String, TransMeta> transConfig = readTransConfig(config.getTransConfig());
        Map<String, String> modelDataTypes = readModelDataTypes(config.getModelSchema());

        List<DynamicCrudExcelColumn> columns = new ArrayList<>();
        for (JsonNode node : schema) {
            String field = getFirstText(node, "field", "prop", "dataIndex", "key");
            if (StringUtils.isBlank(field) || isActionField(field)) {
                continue;
            }
            DynamicCrudExcelColumn meta = new DynamicCrudExcelColumn();
            meta.setField(field);
            meta.setLabel(StringUtils.defaultIfBlank(getFirstText(node, "label", "title", "columnName"), field));
            meta.setType(getFirstText(node, "type", "componentType"));
            meta.setDataType(StringUtils.defaultIfBlank(modelDataTypes.get(field), inferDataType(meta.getType())));
            meta.setDictType(resolveDictType(field, node, transConfig));
            meta.setRequired(resolveRequired(node));
            meta.setExampleValue(getFirstText(node, "exampleValue", "example", "defaultValue"));
            meta.setDescription(getFirstText(node, "description", "help", "placeholder"));
            columns.add(meta);
        }
        return applyImportColumnConfig(config, columns);
    }

    private List<DynamicCrudExcelColumn> applyExportColumnConfig(
            AiCrudConfig config, List<DynamicCrudExcelColumn> schemaColumns) {
        List<ExcelColumnConfig> configuredColumns = loadColumnConfigs(config.getConfigKey());
        if (configuredColumns.isEmpty()) {
            return schemaColumns;
        }

        Map<String, DynamicCrudExcelColumn> schemaColumnMap = schemaColumns.stream()
                .collect(Collectors.toMap(
                        DynamicCrudExcelColumn::getField,
                        column -> column,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<DynamicCrudExcelColumn> orderedColumns = new ArrayList<>();
        for (ExcelColumnConfig columnConfig : configuredColumns) {
            if (Boolean.FALSE.equals(columnConfig.getExport()) || StringUtils.isBlank(columnConfig.getFieldName())) {
                continue;
            }
            DynamicCrudExcelColumn schemaColumn = schemaColumnMap.get(columnConfig.getFieldName());
            if (schemaColumn == null) {
                continue;
            }
            orderedColumns.add(mergeColumnConfig(schemaColumn, columnConfig));
        }
        return orderedColumns.isEmpty() ? schemaColumns : orderedColumns;
    }

    private List<DynamicCrudExcelColumn> applyImportColumnConfig(
            AiCrudConfig config, List<DynamicCrudExcelColumn> schemaColumns) {
        List<ExcelColumnConfig> configuredColumns = loadColumnConfigs(config.getConfigKey());
        if (configuredColumns.isEmpty()) {
            return schemaColumns;
        }

        Map<String, ExcelColumnConfig> configMap = configuredColumns.stream()
                .filter(column -> StringUtils.isNotBlank(column.getFieldName()))
                .collect(Collectors.toMap(
                        ExcelColumnConfig::getFieldName,
                        column -> column,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<DynamicCrudExcelColumn> orderedColumns = new ArrayList<>();
        Set<String> appendedFields = new LinkedHashSet<>();
        for (ExcelColumnConfig columnConfig : configuredColumns) {
            if (Boolean.FALSE.equals(columnConfig.getImportable()) || StringUtils.isBlank(columnConfig.getFieldName())) {
                continue;
            }
            DynamicCrudExcelColumn schemaColumn = findColumn(schemaColumns, columnConfig.getFieldName());
            if (schemaColumn == null) {
                continue;
            }
            orderedColumns.add(mergeColumnConfig(schemaColumn, columnConfig));
            appendedFields.add(schemaColumn.getField());
        }
        for (DynamicCrudExcelColumn schemaColumn : schemaColumns) {
            if (appendedFields.contains(schemaColumn.getField())) {
                continue;
            }
            ExcelColumnConfig columnConfig = configMap.get(schemaColumn.getField());
            if (Boolean.FALSE.equals(columnConfig != null ? columnConfig.getImportable() : null)) {
                continue;
            }
            orderedColumns.add(columnConfig == null ? schemaColumn : mergeColumnConfig(schemaColumn, columnConfig));
        }
        return orderedColumns;
    }

    private DynamicCrudExcelColumn findColumn(List<DynamicCrudExcelColumn> columns, String fieldName) {
        return columns.stream()
                .filter(column -> fieldName.equals(column.getField()))
                .findFirst()
                .orElse(null);
    }

    private DynamicCrudExcelColumn mergeColumnConfig(
            DynamicCrudExcelColumn source, ExcelColumnConfig columnConfig) {
        DynamicCrudExcelColumn target = new DynamicCrudExcelColumn();
        target.setField(source.getField());
        target.setLabel(StringUtils.defaultIfBlank(columnConfig.getColumnName(), source.getLabel()));
        target.setType(source.getType());
        target.setDataType(source.getDataType());
        target.setDictType(StringUtils.defaultIfBlank(columnConfig.getDictType(), source.getDictType()));
        target.setTargetField(source.getTargetField());
        target.setRequired(columnConfig.getRequired() != null ? columnConfig.getRequired() : source.isRequired());
        target.setExampleValue(StringUtils.defaultIfBlank(columnConfig.getExampleValue(), source.getExampleValue()));
        target.setDescription(StringUtils.defaultIfBlank(columnConfig.getValidationMessage(), source.getDescription()));
        return target;
    }

    private List<ExcelColumnConfig> loadColumnConfigs(String configKey) {
        ExcelConfigProvider provider = excelConfigProvider.getIfAvailable();
        if (provider == null || StringUtils.isBlank(configKey)) {
            return List.of();
        }
        try {
            List<ExcelColumnConfig> columns = provider.getColumnConfigs(configKey);
            if (columns == null || columns.isEmpty()) {
                return List.of();
            }
            return columns.stream()
                    .filter(Objects::nonNull)
                    .sorted((left, right) -> {
                        Integer leftOrder = left.getOrderNum() == null ? Integer.MAX_VALUE : left.getOrderNum();
                        Integer rightOrder = right.getOrderNum() == null ? Integer.MAX_VALUE : right.getOrderNum();
                        return leftOrder.compareTo(rightOrder);
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 读取Excel列配置失败, configKey={}", configKey, e);
            return List.of();
        }
    }

    private List<List<String>> buildHeaders(List<DynamicCrudExcelColumn> columns) {
        return columns.stream()
                .map(column -> List.of(column.getLabel()))
                .toList();
    }

    private void writeWorkbook(HttpServletResponse response,
                               String fileName,
                               String sheetName,
                               List<List<String>> headers,
                               List<List<Object>> rows) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);
            EasyExcel.write(response.getOutputStream())
                    .head(headers)
                    .sheet(sheetName)
                    .doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException("写入Excel失败: " + e.getMessage());
        }
    }

    private void writeImportTemplateWorkbook(HttpServletResponse response,
                                             String fileName,
                                             List<DynamicCrudExcelColumn> columns) {
        try {
            response.setContentType(XLSX_MIME);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);
            ExcelImportTemplateWriter.write(
                    response.getOutputStream(),
                    "导入数据",
                    columns.stream()
                            .map(column -> excelValueAdapter.toImportTemplateColumn(column, resolveTenantId()))
                            .toList()
            );
        } catch (IOException e) {
            throw new BusinessException("写入导入模板失败: " + e.getMessage());
        }
    }

    private void writeAsyncWorkbook(Path targetFile,
                                    String configKey,
                                    DynamicCrudQuery query,
                                    List<DynamicCrudExcelColumn> columns,
                                    ExportExecutionContext context) throws IOException {
        List<List<String>> headers = buildHeaders(columns);
        ExcelWriter excelWriter = null;
        try (OutputStream outputStream = Files.newOutputStream(targetFile)) {
            excelWriter = EasyExcel.write(outputStream)
                    .head(headers)
                    .build();
            try {
                WriteSheet writeSheet = EasyExcel.writerSheet("数据").build();
                long exported = 0L;
                int pageNum = 1;
                while (exported < context.totalCount()) {
                    List<Map<String, Object>> rows = dynamicCrudService.selectExportPageRows(
                            configKey, query, pageNum, context.batchSize(), context.dataScopeContext());
                    if (rows.isEmpty()) {
                        break;
                    }
                    List<List<Object>> data = rows.stream()
                            .map(row -> excelValueAdapter.buildExportRow(row, columns))
                            .toList();
                    excelWriter.write(data, writeSheet);
                    exported += rows.size();
                    updateTaskProgress(context.taskId(), exported, context.totalCount());
                    pageNum++;
                }
                if (context.totalCount() == 0) {
                    updateTaskProgress(context.taskId(), 0L, 0L);
                }
            } finally {
                if (excelWriter != null) {
                    excelWriter.finish();
                }
            }
        }
    }

    private AiCrudExportTask createExportTask(AiCrudConfig config,
                                              DynamicCrudQuery query,
                                              long totalCount,
                                              int keepHours) {
        AiCrudExportTask task = new AiCrudExportTask();
        task.setTenantId(resolveTenantId());
        task.setConfigKey(config.getConfigKey());
        task.setExportName(StringUtils.defaultIfBlank(config.getAppName(),
                StringUtils.defaultIfBlank(config.getTableComment(), config.getConfigKey())));
        task.setFileName(buildFileName(config, "导出数据"));
        task.setStatus(DynamicCrudExportStatus.PENDING.getCode());
        task.setTotalCount(totalCount);
        task.setExportedCount(0L);
        task.setProgress(0);
        task.setQueryParams(writeQueryParams(query));
        task.setExpireTime(LocalDateTime.now().plusHours(keepHours));
        task.setCreateBy(SessionHelper.getUserId());
        task.setCreateDept(SessionHelper.getMainOrgId());
        task.setUpdateBy(SessionHelper.getUserId());
        exportTaskMapper.insert(task);
        return task;
    }

    private void markTaskRunning(Long taskId) {
        AiCrudExportTask update = new AiCrudExportTask();
        update.setId(taskId);
        update.setStatus(DynamicCrudExportStatus.RUNNING.getCode());
        update.setProgress(0);
        update.setExportedCount(0L);
        exportTaskMapper.updateById(update);
    }

    private void updateTaskProgress(Long taskId, long exportedCount, long totalCount) {
        AiCrudExportTask update = new AiCrudExportTask();
        update.setId(taskId);
        update.setExportedCount(exportedCount);
        update.setProgress(totalCount <= 0 ? 100 : Math.min(99, (int) ((exportedCount * 100) / totalCount)));
        exportTaskMapper.updateById(update);
    }

    private String writeQueryParams(DynamicCrudQuery query) {
        try {
            return objectMapper.writeValueAsString(query == null ? new DynamicCrudQuery() : query);
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 序列化导出参数失败", e);
            return "{}";
        }
    }

    private DataScopeContext captureDataScopeContext() {
        try {
            return dataScopeService.getCurrentUserDataScope();
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 捕获数据权限上下文失败，将由查询链路按当前上下文处理", e);
            return null;
        }
    }

    private int readPositiveIntConfig(String configKey, int defaultValue, int minValue, int maxValue) {
        try {
            String value = exportTaskMapper.selectConfigValue(resolveTenantId(), configKey);
            if (StringUtils.isBlank(value)) {
                return defaultValue;
            }
            int parsed = Integer.parseInt(value.trim());
            return Math.max(minValue, Math.min(maxValue, parsed));
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 读取系统参数失败, configKey={}", configKey, e);
            return defaultValue;
        }
    }

    private Long resolveTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        tenantId = SessionHelper.getTenantId();
        return tenantId != null ? tenantId : 1L;
    }

    private JsonNode readArray(String json, String fieldName) {
        try {
            JsonNode node = StringUtils.isBlank(json) ? objectMapper.createArrayNode() : objectMapper.readTree(json);
            if (!node.isArray()) {
                throw new BusinessException(fieldName + "必须是数组");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(fieldName + "解析失败: " + e.getMessage());
        }
    }

    private Map<String, TransMeta> readTransConfig(String json) {
        Map<String, TransMeta> result = new HashMap<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return result;
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode rule = entry.getValue();
                TransMeta meta = new TransMeta();
                meta.setDictType(getFirstText(rule, "dictType"));
                meta.setTargetField(StringUtils.defaultIfBlank(getFirstText(rule, "targetField"), entry.getKey() + "Name"));
                result.put(entry.getKey(), meta);
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 解析transConfig失败", e);
        }
        return result;
    }

    private Map<String, String> readModelDataTypes(String modelSchemaJson) {
        Map<String, String> result = new HashMap<>();
        if (StringUtils.isBlank(modelSchemaJson)) {
            return result;
        }
        try {
            JsonNode fields = objectMapper.readTree(modelSchemaJson).get("fields");
            if (fields == null || !fields.isArray()) {
                return result;
            }
            for (JsonNode field : fields) {
                String fieldName = getFirstText(field, "field");
                String dataType = getFirstText(field, "dataType");
                if (StringUtils.isNotBlank(fieldName) && StringUtils.isNotBlank(dataType)) {
                    result.put(fieldName, dataType.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            log.warn("[DynamicCrudExcelService] 解析modelSchema字段类型失败", e);
        }
        return result;
    }

    private String resolveDictType(String field, JsonNode node, Map<String, TransMeta> transConfig) {
        String dictType = getFirstText(node, "dictType", "_dictType");
        if (StringUtils.isBlank(dictType) && node.has("render") && node.get("render").isObject()) {
            dictType = getFirstText(node.get("render"), "dictType");
        }
        if (StringUtils.isBlank(dictType) && transConfig.containsKey(field)) {
            dictType = transConfig.get(field).getDictType();
        }
        return dictType;
    }

    private String resolveTargetField(String field, JsonNode node, Map<String, TransMeta> transConfig) {
        TransMeta meta = transConfig.get(field);
        if (meta != null && StringUtils.isNotBlank(meta.getTargetField())) {
            return meta.getTargetField();
        }
        if (node != null && node.has("render") && node.get("render").isObject()) {
            JsonNode render = node.get("render");
            String targetField = getFirstText(render, "targetField");
            if (StringUtils.isNotBlank(targetField)) {
                return targetField;
            }
            String renderType = StringUtils.defaultIfBlank(getFirstText(render, "type"), "").toLowerCase(Locale.ROOT);
            if (Set.of("orgname", "username", "regionname", "fileupload", "imageupload").contains(renderType)) {
                return field + "Name";
            }
        }
        String componentType = StringUtils.defaultIfBlank(getFirstText(node, "type", "componentType"), "").toLowerCase(Locale.ROOT);
        if (Set.of("orgtreeselect", "userselect", "regiontreeselect", "treeselect").contains(componentType)) {
            return field + "Name";
        }
        return null;
    }

    private boolean resolveRequired(JsonNode node) {
        if (node.has("required") && node.get("required").asBoolean(false)) {
            return true;
        }
        JsonNode rules = node.get("rules");
        if (rules == null || !rules.isArray()) {
            return false;
        }
        for (JsonNode rule : rules) {
            if (rule.has("required") && rule.get("required").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private String getFirstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && StringUtils.isNotBlank(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private boolean isActionField(String field) {
        return "action".equals(field) || "actions".equals(field);
    }

    private String inferDataType(String componentType) {
        String normalized = StringUtils.defaultIfBlank(componentType, "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "number", "inputnumber", "input-number" -> "decimal";
            case "date" -> "date";
            case "datetime" -> "datetime";
            case "time" -> "time";
            default -> "";
        };
    }

    private String buildFileName(AiCrudConfig config, String suffix) {
        String baseName = StringUtils.defaultIfBlank(config.getAppName(),
                StringUtils.defaultIfBlank(config.getTableComment(), config.getConfigKey()));
        String fileName = baseName + "_" + suffix + ".xlsx";
        return fileName.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    public record ExportExecutionContext(Long taskId,
                                         Long tenantId,
                                         Long userId,
                                         Long createDept,
                                         DataScopeContext dataScopeContext,
                                         long totalCount,
                                         int batchSize,
                                         int keepHours) {
    }

    @Data
    private static class TransMeta {
        private String dictType;
        private String targetField;
    }

    @Data
    private static class ExcelReadResult {
        private Map<Integer, String> headers = new LinkedHashMap<>();
        private List<DynamicCrudExcelRow> rows = new ArrayList<>();
    }

    private static class MapRowReadListener extends AnalysisEventListener<Map<Integer, Object>> {

        private final Map<Integer, String> headers = new LinkedHashMap<>();
        private final List<DynamicCrudExcelRow> rows = new ArrayList<>();

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            headers.clear();
            headers.putAll(headMap);
        }

        @Override
        public void invoke(Map<Integer, Object> data, AnalysisContext context) {
            DynamicCrudExcelRow row = new DynamicCrudExcelRow();
            row.setRowNum(context.readRowHolder().getRowIndex() + 1);
            row.setValues(data.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .filter(entry -> Objects.nonNull(entry.getValue()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (left, right) -> left,
                            LinkedHashMap::new
                    )));
            rows.add(row);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            log.debug("[DynamicCrudExcelService] Excel解析完成, rows={}", rows.size());
        }

        public Map<Integer, String> getHeaders() {
            return headers;
        }

        public List<DynamicCrudExcelRow> getRows() {
            return rows;
        }
    }
}
