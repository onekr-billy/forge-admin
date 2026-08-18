package com.mdframe.forge.plugin.generator.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudExportTask;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudExportResult;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudImportResult;
import com.mdframe.forge.plugin.generator.dto.DynamicCrudQuery;
import com.mdframe.forge.plugin.generator.service.DynamicCrudExcelService;
import com.mdframe.forge.plugin.generator.service.DynamicCrudService;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessEventPublisher;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/ai/crud/{configKey}")
@RequiredArgsConstructor
public class DynamicCrudController {

    private static final String SEARCH_TYPES_PARAM = "_searchTypes";
    private static final int MAX_SEARCH_TYPE_FIELDS = 100;

    private final DynamicCrudService dynamicCrudService;
    private final DynamicCrudExcelService dynamicCrudExcelService;
    private final BusinessEventPublisher businessEventPublisher;

    @ApiEncrypt
    @GetMapping("/page")
    public RespInfo<Page<Map<String, Object>>> page(@PathVariable String configKey,
                                                     PageQuery pageQuery,
                                                     DynamicCrudQuery query,
                                                     @RequestParam Map<String, Object> requestParams) {
        Page<Map<String, Object>> data = dynamicCrudService.selectPage(
                configKey, pageQuery, buildQuery(query, requestParams));
        if (log.isDebugEnabled()) {
            log.debug("DynamicCrudController#page configKey={}, pageNum={}, pageSize={}, total={}, records={}",
                    configKey, pageQuery.getPageNum(), pageQuery.getPageSize(), data.getTotal(), data.getRecords().size());
        }
        return RespInfo.success(data);
    }

    @ApiEncrypt
    @GetMapping("/tree")
    public RespInfo<List<Map<String, Object>>> tree(@PathVariable String configKey,
                                                    @RequestParam(required = false) String parentValue,
                                                    @RequestParam(required = false) String parentId,
                                                    @RequestParam(required = false) String loadMode,
                                                    @RequestParam(required = false) String orderByColumn,
                                                    @RequestParam(required = false) String isAsc) {
        return RespInfo.success(dynamicCrudService.selectTree(configKey, parentValue, parentId, loadMode, orderByColumn, isAsc));
    }

    @ApiEncrypt
    @GetMapping("/{id}")
    public RespInfo<Map<String, Object>> getById(@PathVariable String configKey,
                                                  @PathVariable String id) {
        return RespInfo.success(dynamicCrudService.selectById(configKey, id));
    }

    @ApiEncrypt
    @ApiDecrypt
    @PostMapping
    public RespInfo<Map<String, Object>> create(@PathVariable String configKey,
                                                @RequestBody Map<String, Object> data) {
        Map<String, Object> createdData = dynamicCrudService.insert(configKey, data);
        // 发布记录创建事件，触发器引擎异步处理
        businessEventPublisher.publishRecordCreated(configKey, createdData != null ? createdData : data);
        return RespInfo.success(createdData != null ? createdData : data);
    }

    @ApiEncrypt
    @ApiDecrypt
    @PutMapping
    public RespInfo<Void> update(@PathVariable String configKey,
                                  @RequestBody Map<String, Object> data) {
        // 获取更新前的数据用于变更检测
        Map<String, Object> previousData = null;
        Object recordId = dynamicCrudService.resolveRecordId(configKey, data);
        if (recordId != null) {
            try {
                previousData = dynamicCrudService.selectById(configKey, recordId);
            } catch (Exception e) {
                log.debug("获取更新前数据失败: {}", e.getMessage());
            }
        }
        dynamicCrudService.updateById(configKey, data);
        // 发布记录更新事件
        businessEventPublisher.publishRecordUpdated(configKey, data, previousData);
        return RespInfo.success();
    }

    @ApiEncrypt
    @DeleteMapping("/{id}")
    public RespInfo<Void> delete(@PathVariable String configKey,
                                  @PathVariable String id) {
        dynamicCrudService.deleteById(configKey, id);
        // 发布记录删除事件
        businessEventPublisher.publishRecordDeleted(configKey, String.valueOf(id));
        return RespInfo.success();
    }

    @PostMapping("/import")
    public RespInfo<DynamicCrudImportResult> importExcel(@PathVariable String configKey,
                                                         @RequestParam("file") MultipartFile file) {
        return RespInfo.success(dynamicCrudExcelService.importExcel(configKey, file));
    }

    @PostMapping("/export")
    public RespInfo<DynamicCrudExportResult> exportExcel(@PathVariable String configKey,
                                                         @RequestBody(required = false) Map<String, Object> requestBody,
                                                         HttpServletResponse response) {
        DynamicCrudExportResult result = dynamicCrudExcelService.exportExcel(
                configKey, buildQueryFromBody(requestBody), response);
        return Boolean.TRUE.equals(result.getAsync()) ? RespInfo.success(result) : null;
    }

    @GetMapping("/export/tasks")
    public RespInfo<Page<AiCrudExportTask>> exportTasks(@PathVariable String configKey,
                                                        PageQuery pageQuery) {
        return RespInfo.success(dynamicCrudExcelService.selectExportTaskPage(configKey, pageQuery));
    }

    @GetMapping("/export/tasks/{taskId}")
    public RespInfo<AiCrudExportTask> exportTask(@PathVariable String configKey,
                                                 @PathVariable Long taskId) {
        return RespInfo.success(dynamicCrudExcelService.selectExportTask(configKey, taskId));
    }

    @GetMapping("/import-template")
    public void downloadImportTemplate(@PathVariable String configKey,
                                       HttpServletResponse response) {
        dynamicCrudExcelService.downloadImportTemplate(configKey, response);
    }

    private DynamicCrudQuery buildQuery(DynamicCrudQuery query, Map<String, Object> requestParams) {
        DynamicCrudQuery result = query != null ? query : new DynamicCrudQuery();
        if (result.getSearchParams() == null || result.getSearchParams().isEmpty()) {
            result.setSearchParams(filterSearchParams(requestParams));
        }
        if (result.getSearchTypeMap() == null || result.getSearchTypeMap().isEmpty()) {
            result.setSearchTypeMap(parseSearchTypeMap(requestParams == null
                    ? null
                    : requestParams.get(SEARCH_TYPES_PARAM)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private DynamicCrudQuery buildQueryFromBody(Map<String, Object> requestBody) {
        DynamicCrudQuery query = new DynamicCrudQuery();
        if (requestBody == null || requestBody.isEmpty()) {
            return query;
        }
        Object searchParams = requestBody.get("searchParams");
        if (searchParams instanceof Map<?, ?> params) {
            query.setSearchParams((Map<String, Object>) params);
        } else {
            query.setSearchParams(filterSearchParams(requestBody));
        }
        query.setSearchTypeMap(parseSearchTypeMap(requestBody.get(SEARCH_TYPES_PARAM)));
        return query;
    }

    private Map<String, Object> filterSearchParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Set<String> controlParams = Set.of(
                "pageNum", "pageSize", "orderByColumn", "isAsc", "searchParams", "designPreview",
                SEARCH_TYPES_PARAM);
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!controlParams.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private Map<String, String> parseSearchTypeMap(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        Map<?, ?> source;
        if (rawValue instanceof Map<?, ?> values) {
            source = values;
        } else {
            String text = String.valueOf(rawValue).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                JSONObject parsed = JSON.parseObject(text);
                source = parsed == null ? Map.of() : parsed;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (result.size() >= MAX_SEARCH_TYPE_FIELDS) {
                break;
            }
            String field = entry.getKey() == null ? "" : String.valueOf(entry.getKey()).trim();
            String searchType = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (!field.isEmpty() && field.length() <= 128 && !searchType.isEmpty() && searchType.length() <= 32) {
                result.put(field, searchType);
            }
        }
        return result.isEmpty() ? null : result;
    }
}
