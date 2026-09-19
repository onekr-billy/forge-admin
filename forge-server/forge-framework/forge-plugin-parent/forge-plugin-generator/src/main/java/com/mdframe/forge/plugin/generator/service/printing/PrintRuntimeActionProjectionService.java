package com.mdframe.forge.plugin.generator.service.printing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.dto.AiCrudConfigRenderVO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessApplicationRuntimeService;
import com.mdframe.forge.plugin.print.enums.PrintScene;
import com.mdframe.forge.plugin.print.mapper.PrintTemplateMapper;
import com.mdframe.forge.plugin.print.service.PrintIdentity;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** 页面级固定绑定投影成现有 route 动作；真正打印时 Provider 再核验记录与版本。 */
@Component
@RequiredArgsConstructor
public class PrintRuntimeActionProjectionService {
    private final PrintIdentity identity;
    private final BusinessApplicationRuntimeService runtime;
    private final BusinessApplicationVersionMapper versions;
    private final PrintApplicationSnapshotCodec snapshots;
    private final LowcodePrintSourceResolver sources;
    private final PrintTemplateMapper templates;
    private final ObjectMapper json;

    public AiCrudConfigRenderVO overlay(String configKey, Long applicationId, String pageId,
                                       AiCrudConfigRenderVO config, boolean designPreview) {
        if (config == null || designPreview || applicationId == null || pageId == null
                || !pageId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || !SessionHelper.hasPermission("print:execute")) {
            return config;
        }
        var actor = identity.current();
        com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO portal;
        try {
            portal = runtime.runtimeById(applicationId);
        } catch (BusinessException denied) {
            return config;
        }
        if (portal == null || portal.getApplication() == null) return config;
        var version = versions.selectVersion(actor.tenantId(), applicationId, portal.getVersionNo());
        if (version == null) return config;
        var allowed = json.createObjectNode();
        allowed.putObject("application").set("options", json.valueToTree(map(portal.getApplication().getOptions())));
        allowed.set("objects", json.valueToTree(portal.getObjects()));
        String rowKey = config.getRowKey();
        if (rowKey == null || !rowKey.matches("[A-Za-z_][A-Za-z0-9_]*")) return config;
        Map<PrintScene, Map<String, Object>> actions = new LinkedHashMap<>();
        for (var binding : snapshots.read(version.getSnapshotJson(), applicationId)) {
            var source = binding.source();
            if (!pageId.equals(source.pageId()) || (binding.scene() != PrintScene.LIST && binding.scene() != PrintScene.DETAIL)) continue;
            if (!SessionHelper.hasPermission("ai:business:" + source.objectCode() + ":query")
                    && !SessionHelper.hasPermission("ai:business:" + source.objectCode() + ":list")) continue;
            com.fasterxml.jackson.databind.JsonNode object;
            try { object = sources.object(allowed, source, false); }
            catch (BusinessException denied) { continue; }
            if (!Objects.equals(configKey, object.path("configKey").asText())) continue;
            var template = templates.selectScoped(actor.tenantId(), binding.templateId());
            if (template == null || !EnableStatus.ENABLED.matches(template.getStatus())
                    || !applicationId.equals(template.getApplicationId()) || !source.key().equals(template.getSourceKey())) continue;
            actions.put(binding.scene(), action(binding, rowKey));
        }
        if (actions.isEmpty()) return config;
        Map<String, Object> options = map(config.getOptions());
        List<Map<String, Object>> runtimeActions = maps(options.get("runtimeActions"));
        runtimeActions.removeIf(item -> String.valueOf(item.get("key")).startsWith("forgePrint:"));
        runtimeActions.addAll(actions.values());
        options.put("runtimeActions", runtimeActions);
        config.setOptions(options);
        // 普通表格可能完全没有操作列；运行入口仍应可达。
        List<Map<String, Object>> columns = maps(config.getColumnsSchema());
        if (actions.containsKey(PrintScene.LIST) && columns.stream().noneMatch(column ->
                List.of("action", "actions", "operation", "operations").contains(String.valueOf(column.get("key"))))) {
            columns.add(new LinkedHashMap<>(Map.of("key", "actions", "title", "操作", "width", 150, "actions", List.of())));
            config.setColumnsSchema(columns);
        }
        return config;
    }

    private Map<String, Object> action(PrintApplicationSnapshotCodec.Binding binding, String rowKey) {
        var source = binding.source();
        List<Map<String, String>> params = new ArrayList<>();
        params.add(param("applicationId", String.valueOf(source.applicationId())));
        params.add(param("sourceType", source.sourceType().name()));
        params.add(param("pageId", source.pageId()));
        params.add(param("objectCode", source.objectCode()));
        params.add(param("scene", binding.scene().name()));
        params.add(Map.of("name", "recordId", "sourceType", "rowField", "sourceField", rowKey));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("key", "forgePrint:" + binding.scene().name());
        action.put("label", "打印");
        action.put("actionType", "route");
        action.put("routePath", "/print/preview");
        action.put("position", binding.scene() == PrintScene.LIST ? "row" : "detail");
        action.put("permissionCode", "print:execute");
        action.put("openTarget", "_blank");
        action.put("params", params);
        return action;
    }

    private Map<String, String> param(String name, String value) {
        return Map.of("name", name, "sourceType", "static", "value", value);
    }

    private Map<String, Object> map(Object value) {
        try {
            if (value instanceof String text) return json.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() { });
            if (value instanceof Map<?, ?>) return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() { });
            return new LinkedHashMap<>();
        } catch (Exception ex) { throw new BusinessException("打印页面配置无效"); }
    }

    private List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) list.forEach(item -> result.add(map(item)));
        return result;
    }
}
