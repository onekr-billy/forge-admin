package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApp;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.mapper.BusinessAppMapper;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将应用级运行交互叠加到对象级 CRUD 配置。
 *
 * <p>对象可以被多个应用复用，因而 flowInteraction 不能写回 AiCrudConfig 全局配置；
 * 只有携带业务应用入口上下文时，才从该应用的正式快照读取并叠加。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationRuntimeConfigOverlayService {

    private final BusinessAppMapper businessAppMapper;
    private final BusinessApplicationRuntimeService runtimeService;
    private final ObjectMapper objectMapper;

    public AiCrudConfigRenderVO overlay(String configKey, Long appId, AiCrudConfigRenderVO renderConfig) {
        if (renderConfig == null || appId == null || StringUtils.isBlank(configKey)) {
            return renderConfig;
        }
        AiBusinessApp entry = businessAppMapper.selectEntityById(resolveTenantId(), appId);
        if (entry == null || !StringUtils.equals(configKey, entry.getConfigKey())
                || entry.getApplicationId() == null) {
            return renderConfig;
        }

        BusinessApplicationRuntimeVO runtime;
        try {
            runtime = runtimeService.runtimeById(entry.getApplicationId());
        } catch (BusinessException ignored) {
            // 旧入口或尚未发布的应用仍可使用对象级 CRUD；此时不叠加应用交互。
            return renderConfig;
        }
        if (!containsEntry(runtime, appId)) {
            return renderConfig;
        }

        Map<String, Object> applicationOptions = readMap(runtime.getApplication() == null
                ? null : runtime.getApplication().getOptions());
        Map<String, Object> builder = readMap(applicationOptions.get("inAppBuilder"));
        Object flowInteraction = builder.get("flowInteraction");
        if (flowInteraction == null) {
            return renderConfig;
        }

        Map<String, Object> options = readMap(renderConfig.getOptions());
        options.put("flowInteraction", flowInteraction);
        renderConfig.setOptions(options);
        return renderConfig;
    }

    private boolean containsEntry(BusinessApplicationRuntimeVO runtime, Long appId) {
        if (runtime == null || runtime.getEntries() == null) {
            return false;
        }
        return runtime.getEntries().stream()
                .anyMatch(entry -> entry != null && appId.equals(entry.getId()));
    }

    private Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return objectMapper.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private Long resolveTenantId() {
        try {
            Long tenantId = SessionHelper.getTenantId();
            return tenantId == null ? 1L : tenantId;
        } catch (Exception ignored) {
            return 1L;
        }
    }
}
