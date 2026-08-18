package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessApplicationPageMenuDTO;
import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 发布或回滚时把已发布页面树投影为系统菜单资源。 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationPageMenuPublishService {

    private static final String ROOT_NODE_ID = "__application_menu_root__";
    private static final String RUNTIME_COMPONENT = "app-center/application-runtime.[applicationCode]";

    private final MenuRegisterAdapter menuRegisterAdapter;

    public Map<String, Long> sync(Map<String, Object> snapshot) {
        Map<String, Object> application = map(snapshot.get("application"));
        String applicationCode = StringUtils.trimToNull(string(application.get("applicationCode")));
        if (applicationCode == null) {
            return Map.of();
        }
        Map<String, Object> builder = map(map(application.get("options")).get("inAppBuilder"));
        List<Map<String, Object>> nodes = maps(builder.get("nodes"));
        if (nodes.isEmpty()) {
            return menuRegisterAdapter.syncApplicationPageMenus(applicationCode, List.of());
        }
        List<Map<String, Object>> visibleNodes = nodes.stream().filter(this::systemMenuVisible).toList();
        if (visibleNodes.isEmpty()) {
            return menuRegisterAdapter.syncApplicationPageMenus(applicationCode, List.of());
        }
        java.util.Set<String> visibleNodeIds = visibleNodes.stream()
                .map(node -> StringUtils.trimToNull(string(node.get("id"))))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        String rootPerms = permission(applicationCode, "root");
        List<BusinessApplicationPageMenuDTO> menus = new ArrayList<>();
        BusinessApplicationPageMenuDTO root = menu(ROOT_NODE_ID, null,
                StringUtils.defaultIfBlank(string(application.get("applicationName")), applicationCode),
                "/app-center/application/" + applicationCode + "/runtime", null, rootPerms,
                string(application.get("icon")), 0, true, true);
        menus.add(root);
        for (Map<String, Object> node : visibleNodes) {
            String nodeId = StringUtils.trimToNull(string(node.get("id")));
            if (nodeId == null) {
                continue;
            }
            boolean directory = "group".equalsIgnoreCase(string(node.get("type")));
            String requestedParentId = StringUtils.trimToNull(string(node.get("parentId")));
            String parentNodeId = requestedParentId != null && visibleNodeIds.contains(requestedParentId)
                    ? requestedParentId : ROOT_NODE_ID;
            String title = StringUtils.defaultIfBlank(string(node.get("title")), directory ? "页面组" : "未命名页面");
            menus.add(menu(nodeId, parentNodeId, title,
                    directory ? "/app-center/application/" + applicationCode + "/runtime"
                            : "/app-center/application/" + applicationCode + "/runtime?pageId=" + nodeId,
                    directory ? null : RUNTIME_COMPONENT, permission(applicationCode, nodeId),
                    string(node.get("icon")), integer(node.get("sort")), directory, true));
        }
        Map<String, Long> bindings = menuRegisterAdapter.syncApplicationPageMenus(applicationCode, menus);
        snapshot.put("pageMenu", bindings);
        return bindings;
    }

    public List<String> validate(Map<String, Object> snapshot) {
        Map<String, Object> application = map(snapshot.get("application"));
        Map<String, Object> builder = map(map(application.get("options")).get("inAppBuilder"));
        List<Map<String, Object>> nodes = maps(builder.get("nodes"));
        List<String> errors = new ArrayList<>();
        if (!nodes.isEmpty()) {
            String homePageId = StringUtils.trimToNull(string(builder.get("homePageId")));
            if (homePageId == null || nodes.stream().noneMatch(node -> homePageId.equals(string(node.get("id"))))) {
                errors.add("应用页面未设置有效默认首页");
            }
        }
        return errors;
    }

    private BusinessApplicationPageMenuDTO menu(String nodeId, String parentNodeId, String title, String path,
                                                String component, String perms, String icon, Integer sort,
                                                boolean directory, boolean visible) {
        BusinessApplicationPageMenuDTO item = new BusinessApplicationPageMenuDTO();
        item.setNodeId(nodeId);
        item.setParentNodeId(parentNodeId);
        item.setMenuName(title);
        item.setPath(path);
        item.setComponent(component);
        item.setPerms(perms);
        item.setIcon(icon);
        item.setSort(sort == null ? 0 : sort);
        item.setDirectory(directory);
        item.setVisible(visible);
        return item;
    }

    private boolean systemMenuVisible(Map<String, Object> node) {
        Object value = node.get("systemMenuVisible");
        if (value == null) {
            value = map(node.get("settings")).get("systemMenuVisible");
        }
        return Boolean.TRUE.equals(value);
    }

    private String permission(String applicationCode, String nodeId) {
        return "ai:business:application:" + applicationCode + ":page:" + nodeId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> raw ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of(); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Integer integer(Object value) { try { return value == null ? 0 : Integer.valueOf(String.valueOf(value)); } catch (Exception ignored) { return 0; } }
    private List<Map<String, Object>> maps(Object value) { if (!(value instanceof List<?> list)) return List.of(); return list.stream().filter(Map.class::isInstance).map(this::map).toList(); }
}
