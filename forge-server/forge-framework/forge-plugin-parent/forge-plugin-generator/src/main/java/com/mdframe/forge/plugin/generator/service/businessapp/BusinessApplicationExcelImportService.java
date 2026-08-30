package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationObjectRole;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationExcelFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessFieldDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessObjectDesignerDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationExcelImportResultVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationExcelPreviewVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.mdframe.forge.starter.core.enums.EnableStatus;

/**
 * 将 Excel 首个 Sheet 转换为业务对象设计草稿。
 *
 * <p>这里只识别表头与少量样本用于类型推荐，不导入业务数据，也不自动执行 DDL。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationExcelImportService {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_SAMPLE_ROWS = 50;
    private static final int MAX_SELECT_OPTIONS = 8;
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+(?:\\.\\d+)?$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[ T]\\d{1,2}:\\d{2}(?::\\d{2})?$");
    private static final Set<String> BOOLEAN_VALUES = Set.of(
            "0", "1", "true", "false", "是", "否", "启用", "停用", "有效", "无效");
    private static final Set<String> SUPPORTED_FIELD_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "DATETIME", "SWITCH", "SELECT");

    private final ObjectMapper objectMapper;
    private final BusinessApplicationService applicationService;
    private final BusinessApplicationObjectService applicationObjectService;
    private final BusinessObjectCreateService objectCreateService;
    private final BusinessObjectDesignerService designerService;
    private final BusinessNamingService namingService;

    public BusinessApplicationExcelPreviewVO preview(MultipartFile file) {
        ParsedSheet sheet = readFirstSheet(file);
        BusinessApplicationExcelPreviewVO preview = new BusinessApplicationExcelPreviewVO();
        preview.setFileName(safeFileName(file.getOriginalFilename()));
        preview.setSheetName(sheet.sheetName());
        preview.setSampledRowCount(sheet.rows().size());
        preview.setFields(buildFieldSuggestions(sheet));
        return preview;
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessApplicationExcelImportResultVO initialize(
            Long applicationId,
            MultipartFile file,
            String objectName,
            String objectCode,
            String fieldsJson) {
        AiBusinessApplication application = applicationService.requireEntity(applicationId);
        if (!applicationObjectService.list(applicationId).isEmpty()) {
            throw new BusinessException("当前应用已经包含数据对象，不能重复从 Excel 初始化");
        }
        ParsedSheet sheet = readFirstSheet(file);
        List<BusinessApplicationExcelFieldDTO> confirmedFields = parseConfirmedFields(fieldsJson);
        if (confirmedFields.isEmpty()) {
            confirmedFields = buildFieldSuggestions(sheet);
        }
        validateConfirmedFields(confirmedFields);

        String resolvedObjectName = StringUtils.defaultIfBlank(
                StringUtils.trimToNull(objectName), StringUtils.defaultIfBlank(sheet.sheetName(), application.getApplicationName()));
        String suggestedCode = application.getApplicationCode() + "_" +
                namingService.generateObjectCode(resolvedObjectName);
        String resolvedObjectCode = namingService.normalizeObjectCode(objectCode, suggestedCode);

        BusinessObjectDTO object = new BusinessObjectDTO();
        object.setSuiteCode(application.getSuiteCode());
        object.setObjectName(resolvedObjectName);
        object.setObjectCode(resolvedObjectCode);
        object.setModelCode(namingService.buildModelCode(application.getSuiteCode(), resolvedObjectCode));
        object.setObjectType("MASTER");
        object.setCreateMode("EXCEL_IMPORT");
        object.setDisplayField(resolveDisplayField(confirmedFields));
        object.setDescription("从 Excel 文件「" + safeFileName(file.getOriginalFilename()) + "」识别生成");
        object.setOptions(JSON.toJSONString(Map.of(
                "createMode", "EXCEL_IMPORT",
                "sourceFileName", safeFileName(file.getOriginalFilename()),
                "sourceSheetName", StringUtils.defaultString(sheet.sheetName()))));
        object.setStatus(EnableStatus.ENABLED.getCode());
        Long objectId = objectCreateService.create(object);

        BusinessObjectDesignerDTO designer = new BusinessObjectDesignerDTO();
        designer.setDisplayField(object.getDisplayField());
        designer.setFields(toBusinessFields(confirmedFields));
        designerService.saveDesigner(objectId, designer);

        BusinessApplicationObjectDTO binding = new BusinessApplicationObjectDTO();
        binding.setObjectId(objectId);
        binding.setObjectRole(BusinessApplicationObjectRole.PRIMARY);
        binding.setSortOrder(0);
        binding.setOptions(JSON.toJSONString(Map.of("source", "EXCEL_IMPORT")));
        applicationObjectService.replace(applicationId, List.of(binding));
        return new BusinessApplicationExcelImportResultVO(applicationId, objectId, resolvedObjectCode);
    }

    private ParsedSheet readFirstSheet(MultipartFile file) {
        validateFile(file);
        Map<Integer, String> headers = new LinkedHashMap<>();
        List<Map<Integer, String>> rows = new ArrayList<>();
        String[] sheetName = new String[] {"Sheet1"};
        try (InputStream inputStream = file.getInputStream()) {
            EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    headers.clear();
                    headMap.forEach((index, value) -> headers.put(index, StringUtils.trimToEmpty(value)));
                    if (context.readSheetHolder() != null
                            && StringUtils.isNotBlank(context.readSheetHolder().getSheetName())) {
                        sheetName[0] = context.readSheetHolder().getSheetName();
                    }
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    if (rows.size() < MAX_SAMPLE_ROWS) {
                        rows.add(new LinkedHashMap<>(data));
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 首个 Sheet 的表头与样本已经收集完毕。
                }
            }).headRowNumber(1).sheet(0).doRead();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Excel 文件解析失败，请确认文件未损坏且首行是表头");
        }
        headers.entrySet().removeIf(entry -> StringUtils.isBlank(entry.getValue()));
        if (headers.isEmpty()) {
            throw new BusinessException("Excel 首个 Sheet 未识别到有效表头");
        }
        return new ParsedSheet(sheetName[0], headers, rows);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("Excel 文件不能超过 10MB");
        }
        String name = StringUtils.lowerCase(safeFileName(file.getOriginalFilename()), Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 文件");
        }
    }

    private List<BusinessApplicationExcelFieldDTO> buildFieldSuggestions(ParsedSheet sheet) {
        Set<String> usedFieldCodes = new LinkedHashSet<>();
        Set<String> usedColumnNames = new LinkedHashSet<>();
        List<BusinessApplicationExcelFieldDTO> fields = new ArrayList<>();
        sheet.headers().forEach((columnIndex, headerName) -> {
            List<String> samples = sheet.rows().stream()
                    .map(row -> StringUtils.trimToNull(row.get(columnIndex)))
                    .filter(StringUtils::isNotBlank)
                    .limit(MAX_SAMPLE_ROWS)
                    .toList();
            BusinessApplicationExcelFieldDTO field = inferField(columnIndex, headerName, samples);
            field.setFieldCode(uniqueName(
                    namingService.normalizeFieldCode(null, headerName), usedFieldCodes));
            field.setColumnName(uniqueName(
                    namingService.camelToSnake(field.getFieldCode()), usedColumnNames));
            fields.add(field);
        });
        return fields;
    }

    private BusinessApplicationExcelFieldDTO inferField(
            Integer columnIndex, String headerName, List<String> samples) {
        BusinessApplicationExcelFieldDTO field = new BusinessApplicationExcelFieldDTO();
        field.setColumnIndex(columnIndex);
        field.setHeaderName(headerName);
        field.setRequired(false);
        field.setSearchable(false);
        field.setListVisible(true);
        field.setFormVisible(true);
        field.setLength(128);

        String header = StringUtils.lowerCase(headerName, Locale.ROOT);
        if (matchesAll(samples, DATETIME_PATTERN) || header.contains("时间")) {
            applyType(field, "DATETIME", "datetime", "datetime");
        } else if (matchesAll(samples, DATE_PATTERN) || header.contains("日期")) {
            applyType(field, "DATE", "date", "date");
        } else if (matchesAllBoolean(samples)) {
            applyType(field, "SWITCH", "tinyint", "switch");
            field.setLength(1);
        } else if (matchesAll(samples, INTEGER_PATTERN)) {
            applyType(field, "NUMBER", "bigint", "number");
            field.setLength(19);
            field.setPrecision(0);
        } else if (matchesAll(samples, DECIMAL_PATTERN)) {
            applyType(field, "NUMBER", "decimal", "number");
            field.setLength(18);
            field.setPrecision(resolvePrecision(samples));
        } else if (shouldRecommendSelect(header, samples)) {
            applyType(field, "SELECT", "varchar", "select");
            field.setSuggestedOptions(new ArrayList<>(new LinkedHashSet<>(samples)));
        } else {
            applyType(field, "TEXT", "varchar", "input");
        }
        field.setSearchable(Set.of("TEXT", "SELECT", "DATE", "DATETIME").contains(field.getFieldType()));
        return field;
    }

    private boolean shouldRecommendSelect(String header, List<String> samples) {
        if (samples.size() < 3 || header.contains("名称") || header.contains("备注") || header.contains("说明")) {
            return false;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>(samples);
        return values.size() >= 2 && values.size() <= MAX_SELECT_OPTIONS
                && values.stream().allMatch(value -> value.length() <= 30);
    }

    private boolean matchesAll(List<String> samples, Pattern pattern) {
        return !samples.isEmpty() && samples.stream().allMatch(value -> pattern.matcher(value).matches());
    }

    private boolean matchesAllBoolean(List<String> samples) {
        return !samples.isEmpty() && samples.stream()
                .map(value -> StringUtils.lowerCase(value, Locale.ROOT))
                .allMatch(BOOLEAN_VALUES::contains);
    }

    private int resolvePrecision(List<String> samples) {
        return Math.min(6, samples.stream().mapToInt(value -> {
            int dotIndex = value.indexOf('.');
            return dotIndex < 0 ? 0 : value.length() - dotIndex - 1;
        }).max().orElse(2));
    }

    private void applyType(BusinessApplicationExcelFieldDTO field,
                           String fieldType, String dataType, String componentType) {
        field.setFieldType(fieldType);
        field.setDataType(dataType);
        field.setComponentType(componentType);
    }

    private List<BusinessApplicationExcelFieldDTO> parseConfirmedFields(String fieldsJson) {
        if (StringUtils.isBlank(fieldsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(fieldsJson, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BusinessException("Excel 字段确认配置不是合法 JSON");
        }
    }

    private void validateConfirmedFields(List<BusinessApplicationExcelFieldDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("至少保留一个 Excel 字段");
        }
        Set<String> fieldCodes = new LinkedHashSet<>();
        Set<String> columnNames = new LinkedHashSet<>();
        for (BusinessApplicationExcelFieldDTO field : fields) {
            if (field == null || StringUtils.isBlank(field.getHeaderName())) {
                throw new BusinessException("Excel 字段名称不能为空");
            }
            field.setFieldCode(namingService.normalizeFieldCode(field.getFieldCode(), field.getHeaderName()));
            field.setColumnName(namingService.camelToSnake(
                    StringUtils.defaultIfBlank(field.getColumnName(), field.getFieldCode())));
            field.setFieldType(StringUtils.upperCase(
                    StringUtils.defaultIfBlank(field.getFieldType(), "TEXT"), Locale.ROOT));
            if (!SUPPORTED_FIELD_TYPES.contains(field.getFieldType())) {
                throw new BusinessException("Excel 字段「" + field.getHeaderName() + "」类型不受支持");
            }
            if (!fieldCodes.add(field.getFieldCode()) || !columnNames.add(field.getColumnName())) {
                throw new BusinessException("Excel 字段编码或数据库列名不能重复");
            }
        }
    }

    private List<BusinessFieldDTO> toBusinessFields(List<BusinessApplicationExcelFieldDTO> fields) {
        List<BusinessFieldDTO> result = new ArrayList<>();
        for (int index = 0; index < fields.size(); index++) {
            BusinessApplicationExcelFieldDTO source = fields.get(index);
            BusinessFieldDTO field = new BusinessFieldDTO();
            field.setFieldName(source.getHeaderName());
            field.setFieldCode(source.getFieldCode());
            field.setColumnName(source.getColumnName());
            field.setFieldType(source.getFieldType());
            field.setDataType(source.getDataType());
            field.setComponentType(source.getComponentType());
            field.setLength(source.getLength());
            field.setPrecision(source.getPrecision());
            field.setRequired(Boolean.TRUE.equals(source.getRequired()));
            field.setSearchable(Boolean.TRUE.equals(source.getSearchable()));
            field.setListVisible(source.getListVisible() == null || Boolean.TRUE.equals(source.getListVisible()));
            field.setFormVisible(source.getFormVisible() == null || Boolean.TRUE.equals(source.getFormVisible()));
            field.setImportable(true);
            field.setExportable(true);
            field.setSortable(false);
            field.setSystemField(false);
            field.setReadonly(false);
            field.setFieldStatus("ENABLED");
            field.setSortOrder(index);
            if ("SELECT".equals(source.getFieldType()) && source.getSuggestedOptions() != null) {
                List<Map<String, String>> options = source.getSuggestedOptions().stream()
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .map(value -> Map.of("label", value, "value", value))
                        .toList();
                field.setBasicProps(new LinkedHashMap<>(Map.of("options", options)));
            }
            result.add(field);
        }
        return result;
    }

    private String resolveDisplayField(List<BusinessApplicationExcelFieldDTO> fields) {
        return fields.stream()
                .filter(field -> !"NUMBER".equals(field.getFieldType()) && !"SWITCH".equals(field.getFieldType()))
                .map(BusinessApplicationExcelFieldDTO::getFieldCode)
                .findFirst()
                .orElse(fields.get(0).getFieldCode());
    }

    private String uniqueName(String source, Set<String> usedNames) {
        String base = StringUtils.defaultIfBlank(source, "field");
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate)) {
            candidate = StringUtils.left(base, 58) + suffix++;
        }
        return candidate;
    }

    private String safeFileName(String fileName) {
        String normalized = StringUtils.defaultIfBlank(fileName, "application.xlsx")
                .replace('\\', '/');
        return StringUtils.substringAfterLast("/" + normalized, "/");
    }

    private record ParsedSheet(String sheetName,
                               Map<Integer, String> headers,
                               List<Map<Integer, String>> rows) {
    }
}
