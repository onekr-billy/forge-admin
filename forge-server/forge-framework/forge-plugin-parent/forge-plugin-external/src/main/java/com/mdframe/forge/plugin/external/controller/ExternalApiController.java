package com.mdframe.forge.plugin.external.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mdframe.forge.plugin.external.dto.ExternalApiDTO;
import com.mdframe.forge.plugin.external.dto.ExternalApiQuery;
import com.mdframe.forge.plugin.external.entity.ExternalApi;
import com.mdframe.forge.plugin.external.service.ExternalApiService;
import com.mdframe.forge.plugin.external.support.ExternalQueryContractValidator;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/external/api")
@RequiredArgsConstructor
@ApiEncrypt
@ApiDecrypt
public class ExternalApiController {

    private final ExternalApiService apiService;
    private final ExternalQueryContractValidator queryContractValidator;

    @GetMapping("/page")
    public RespInfo<IPage<ExternalApi>> page(ExternalApiQuery query) {
        return RespInfo.success(apiService.page(query));
    }

    @GetMapping("/{id}")
    public RespInfo<ExternalApi> getById(@PathVariable Long id) {
        return RespInfo.success(apiService.getById(id));
    }

    @PostMapping
    public RespInfo<Void> add(@Validated @RequestBody ExternalApiDTO dto) {
        validateApi(dto);
        ExternalApi entity = convertDtoToEntity(dto);
        apiService.save(entity);
        return RespInfo.success();
    }

    @PutMapping
    public RespInfo<Void> edit(@Validated @RequestBody ExternalApiDTO dto) {
        validateApi(dto);
        ExternalApi entity = convertDtoToEntity(dto);
        apiService.updateById(entity);
        return RespInfo.success();
    }

    @DeleteMapping("/{id}")
    public RespInfo<Void> remove(@PathVariable Long id) {
        apiService.removeById(id);
        return RespInfo.success();
    }

    @GetMapping("/list")
    public RespInfo<List<ExternalApi>> list(@RequestParam(required = false) Long systemId) {
        if (systemId != null) {
            return RespInfo.success(apiService.listBySystemId(systemId));
        }
        return RespInfo.success(apiService.listWithSystem());
    }

    private ExternalApi convertDtoToEntity(ExternalApiDTO dto) {
        ExternalApi entity = new ExternalApi();
        entity.setId(dto.getId());
        entity.setSystemId(dto.getSystemId());
        entity.setApiCode(dto.getApiCode());
        entity.setApiName(dto.getApiName());
        entity.setApiDesc(dto.getApiDesc());
        entity.setExecutionMode(normalizeExecutionMode(dto.getExecutionMode()));
        entity.setApiPath(dto.getApiPath());
        entity.setApiMethod(dto.getApiMethod());
        entity.setRequestContentType(dto.getRequestContentType());
        entity.setRequestHeaders(dto.getRequestHeaders());
        entity.setRequestParams(dto.getRequestParams());
        entity.setRequestBodyTemplate(dto.getRequestBodyTemplate());
        entity.setResponseContentType(dto.getResponseContentType());
        entity.setResponseDataPath(dto.getResponseDataPath());
        entity.setResponseTotalPath(dto.getResponseTotalPath());
        entity.setMockResponseJson(dto.getMockResponseJson());
        entity.setParamMappingEnabled(dto.getParamMappingEnabled());
        entity.setParamMappings(dto.getParamMappings());
        entity.setResponseTransformEnabled(dto.getResponseTransformEnabled());
        entity.setResponseTransformScript(dto.getResponseTransformScript());
        entity.setErrorCodePath(dto.getErrorCodePath());
        entity.setErrorMsgPath(dto.getErrorMsgPath());
        entity.setSuccessCodes(dto.getSuccessCodes());
        entity.setDocFileId(dto.getDocFileId());
        entity.setDocFileName(dto.getDocFileName());
        entity.setRateLimitEnabled(dto.getRateLimitEnabled());
        entity.setRateLimitQps(dto.getRateLimitQps());
        entity.setCacheEnabled(dto.getCacheEnabled());
        entity.setCacheTtl(dto.getCacheTtl());
        entity.setCacheKeyTemplate(dto.getCacheKeyTemplate());
        entity.setPermissionCheckEnabled(dto.getPermissionCheckEnabled());
        entity.setRequiredPermission(dto.getRequiredPermission());
        entity.setLowcodeQueryEnabled(dto.getLowcodeQueryEnabled());
        entity.setInputSchemaJson(dto.getInputSchemaJson());
        entity.setOutputSchemaJson(dto.getOutputSchemaJson());
        entity.setApiStatus(dto.getApiStatus());
        entity.setSortOrder(dto.getSortOrder());
        entity.setRemark(dto.getRemark());
        return entity;
    }

    private void validateApi(ExternalApiDTO dto) {
        if (dto.getSystemId() == null) {
            throw new BusinessException("请选择所属系统");
        }
        requireNotBlank(dto.getApiName(), "接口名称不能为空");
        requireNotBlank(dto.getApiCode(), "接口编码不能为空");
        String executionMode = normalizeExecutionMode(dto.getExecutionMode());
        requireNotBlank(dto.getApiMethod(), "请求方法不能为空");
        if (isMockMode(executionMode)) {
            dto.setExecutionMode(executionMode);
            dto.setApiPath(defaultText(dto.getApiPath(), "/mock"));
            validateMockResponse(dto.getMockResponseJson());
        } else {
            dto.setExecutionMode(executionMode);
            requireNotBlank(dto.getApiPath(), "接口路径不能为空");
        }
        validateJsonObject(dto.getRequestHeaders(), "额外请求头");
        validateJsonObject(dto.getRequestParams(), "固定请求参数");
        validateJsonObject(dto.getParamMappings(), "参数映射规则");
        if ("application/json".equalsIgnoreCase(dto.getRequestContentType())
                && !isBlank(dto.getRequestBodyTemplate())) {
            validateJson(dto.getRequestBodyTemplate(), "请求体模板");
        }
        if (Boolean.TRUE.equals(dto.getPermissionCheckEnabled())) {
            requireNotBlank(dto.getRequiredPermission(), "启用权限校验时必须配置权限码");
        }
        if (Boolean.TRUE.equals(dto.getRateLimitEnabled())
                && (dto.getRateLimitQps() == null || dto.getRateLimitQps() < 1 || dto.getRateLimitQps() > 1000)) {
            throw new BusinessException("限流QPS必须在1到1000之间");
        }
        if (Boolean.TRUE.equals(dto.getCacheEnabled())) {
            if (!"GET".equalsIgnoreCase(dto.getApiMethod())) {
                throw new BusinessException("外部接口缓存仅支持GET请求");
            }
            if (dto.getCacheTtl() != null && (dto.getCacheTtl() < 1 || dto.getCacheTtl() > 86400)) {
                throw new BusinessException("缓存有效期必须在1到86400秒之间");
            }
        }
        if (Boolean.TRUE.equals(dto.getResponseTransformEnabled())) {
            requireNotBlank(dto.getResponseTransformScript(), "启用响应转换时必须配置转换脚本");
        }
        queryContractValidator.validateConfiguration(
                dto.getLowcodeQueryEnabled(), dto.getApiMethod(), dto.getPermissionCheckEnabled(),
                dto.getRequiredPermission(), dto.getInputSchemaJson(), dto.getOutputSchemaJson());
    }

    private void validateJsonObject(String value, String label) {
        if (isBlank(value)) {
            return;
        }
        Object parsed = validateJson(value, label);
        if (!(parsed instanceof com.alibaba.fastjson2.JSONObject)) {
            throw new BusinessException(label + "必须是JSON对象");
        }
    }

    private Object validateJson(String value, String label) {
        try {
            return JSON.parse(value);
        } catch (Exception e) {
            throw new BusinessException(label + "必须是合法JSON");
        }
    }

    private void validateMockResponse(String value) {
        requireNotBlank(value, "Mock模式必须配置Mock响应JSON");
        validateJson(value, "Mock响应JSON");
    }

    private String normalizeExecutionMode(String value) {
        if (isBlank(value)) {
            return "HTTP";
        }
        String mode = value.trim().toUpperCase();
        if (!"HTTP".equals(mode) && !"MOCK".equals(mode)) {
            throw new BusinessException("接口执行模式仅支持HTTP或MOCK");
        }
        return mode;
    }

    private boolean isMockMode(String executionMode) {
        return "MOCK".equalsIgnoreCase(executionMode);
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private void requireNotBlank(String value, String message) {
        if (isBlank(value)) {
            throw new BusinessException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
