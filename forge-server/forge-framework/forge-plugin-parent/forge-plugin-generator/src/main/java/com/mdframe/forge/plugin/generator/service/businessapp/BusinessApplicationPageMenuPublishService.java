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
    private static final String PORTAL_COMPONENT = "app-center/application-portal";

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
        // 收集可见节点的全部祖先，将嵌套 group 节点也纳入可见集合，
        // 确保不同客户端投影后仍能保留完整的父级目录链路。
        java.util.Map<String, Map<String, Object>> nodesById = nodes.stream()
                .filter(node -> StringUtils.trimToNull(string(node.get("id"))) != null)
                .map(node -> Map.entry(StringUtils.trimToNull(string(node.get("id"))), node))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        java.util.Set<String> referencedAncestorIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> visibleNode : visibleNodes) {
            String parentId = StringUtils.trimToNull(string(visibleNode.get("parentId")));
            java.util.Set<String> visited = new java.util.HashSet<>();
            while (parentId != null && visited.add(parentId)) {
                referencedAncestorIds.add(parentId);
                Map<String, Object> parent = nodesById.get(parentId);
                parentId = parent == null ? null : StringUtils.trimToNull(string(parent.get("parentId")));
            }
        }
        List<Map<String, Object>> effectiveNodes = nodes.stream()
                .filter(node -> systemMenuVisible(node)
                        || referencedAncestorIds.contains(StringUtils.trimToNull(string(node.get("id")))))
                .toList();
        List<Map<String, Object>> publishedObjects = maps(snapshot.get("objects"));
        java.util.Set<String> visibleNodeIds = effectiveNodes.stream()
                .map(node -> StringUtils.trimToNull(string(node.get("id"))))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        String rootPerms = permission(applicationCode, "root");
        List<BusinessApplicationPageMenuDTO> menus = new ArrayList<>();
        java.util.LinkedHashSet<String> clientCodes = new java.util.LinkedHashSet<>();
        // Derive clients from nodes that actually have a visible page for the
        // client.  A legacy ADMIN group containing only MOBILE pages must not
        // create an empty management-side application root.
        for (String candidateClient : List.of("pc", "h5")) {
            if (effectiveNodes.stream().anyMatch(node -> isNodeVisibleForClient(node, candidateClient, effectiveNodes))) {
                clientCodes.add(candidateClient);
            }
        }
        if (clientCodes.isEmpty()) {
            clientCodes.add("pc");
        }
        String applicationName = StringUtils.defaultIfBlank(string(application.get("applicationName")), applicationCode);
        String portalIdentifier = resolvePortalIdentifier(application, applicationCode);
        for (String clientCode : clientCodes) {
            menus.add(menu(ROOT_NODE_ID, null, applicationName,
                    "/app/" + portalIdentifier, null, rootPerms,
                    string(application.get("icon")), 0, true, true, clientCode));
            for (Map<String, Object> node : effectiveNodes) {
                String nodeId = StringUtils.trimToNull(string(node.get("id")));
                if (nodeId == null || !isNodeVisibleForClient(node, clientCode, effectiveNodes)) {
                    continue;
                }
                boolean directory = isGroupNode(node);
                String title = resolveMenuTitle(node, directory);
                Integer sort = resolveMenuSort(node);

                // 解析父级：优先使用外部门级 ID（menuParentId），其次使用应用内部页面树的 parentId
                // menuParentId is a management-menu resource ID. H5 has a
                // separate client resource tree, so never reuse a pc parent ID
                // when projecting the mobile copy.
                String externalMenuParentId = resolveExternalMenuParentId(node, clientCode);
                String parentNodeId;
                boolean externalParent;
                if (externalMenuParentId != null) {
                    parentNodeId = externalMenuParentId;
                    externalParent = true;
                } else {
                    String requestedParentId = StringUtils.trimToNull(string(node.get("parentId")));
                    parentNodeId = requestedParentId != null && visibleNodeIds.contains(requestedParentId)
                            ? requestedParentId : ROOT_NODE_ID;
                    externalParent = false;
                }

                String menuPath = resolveMenuPath(application, portalIdentifier,
                        node, nodeId, directory, clientCode, publishedObjects);
                String menuComponent = directory ? null
                        : "h5".equalsIgnoreCase(clientCode) ? menuPath
                        : resolveDesktopMenuComponent(menuPath);
                BusinessApplicationPageMenuDTO item = menu(nodeId, parentNodeId, title,
                        menuPath, menuComponent, permission(applicationCode, nodeId),
                        string(node.get("icon")), sort, directory, true, clientCode);
                item.setExternalParent(externalParent);
                menus.add(item);
            }
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
                                                boolean directory, boolean visible, String clientCode) {
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
        item.setClientCode(clientCode);
        return item;
    }

    private boolean systemMenuVisible(Map<String, Object> node) {
        Object value = node.get("systemMenuVisible");
        if (value == null) {
            value = map(node.get("settings")).get("systemMenuVisible");
        }
        return Boolean.TRUE.equals(value);
    }

    /**
     * 读取页面节点上的菜单名称：优先使用 menuName，其次 settings.menuName，兜底用 title。
     */
    private String resolveMenuTitle(Map<String, Object> node, boolean directory) {
        String menuName = StringUtils.trimToNull(string(node.get("menuName")));
        if (menuName == null) {
            menuName = StringUtils.trimToNull(string(map(node.get("settings")).get("menuName")));
        }
        String title = StringUtils.defaultIfBlank(string(node.get("title")), directory ? "页面组" : "未命名页面");
        return StringUtils.defaultIfBlank(menuName, title);
    }

    /**
     * 解析页面节点的挂载位置对应的 clientCode。
     * <p>优先读取节点自身的 mountTarget，其次读取 settings.mountTarget，
     * 默认 ADMIN（pc）。MOBILE 映射为 h5，其他均映射为 pc。</p>
     */
    private List<String> resolveClientCodes(Map<String, Object> node) {
        String mountTarget = StringUtils.trimToNull(string(node.get("mountTarget")));
        if (mountTarget == null) {
            mountTarget = StringUtils.trimToNull(string(map(node.get("settings")).get("mountTarget")));
        }
        if ("MOBILE".equalsIgnoreCase(mountTarget)) {
            return List.of("h5");
        }
        if ("BOTH".equalsIgnoreCase(mountTarget)) {
            return List.of("pc", "h5");
        }
        return List.of("pc");
    }

    private boolean isNodeVisibleForClient(Map<String, Object> node, String clientCode,
                                           List<Map<String, Object>> effectiveNodes) {
        if (!isGroupNode(node)) {
            return systemMenuVisible(node) && resolveClientCodes(node).contains(clientCode);
        }
        String groupId = StringUtils.trimToNull(string(node.get("id")));
        if (groupId == null) {
            return false;
        }
        return hasVisibleDescendantForClient(groupId, clientCode, effectiveNodes, new java.util.HashSet<>());
    }

    private boolean hasVisibleDescendantForClient(String parentId, String clientCode,
                                                  List<Map<String, Object>> nodes,
                                                  java.util.Set<String> visited) {
        if (!visited.add(parentId)) {
            return false;
        }
        for (Map<String, Object> child : nodes) {
            if (!parentId.equals(StringUtils.trimToNull(string(child.get("parentId"))))) {
                continue;
            }
            if (!isGroupNode(child)
                    && systemMenuVisible(child)
                    && resolveClientCodes(child).contains(clientCode)
                    // An explicitly selected external parent owns this page;
                    // it must not also appear below its old in-app group.
                    && resolveExternalMenuParentId(child, clientCode) == null) {
                return true;
            }
            String childId = StringUtils.trimToNull(string(child.get("id")));
            if (isGroupNode(child) && childId != null
                    && hasVisibleDescendantForClient(childId, clientCode, nodes, new java.util.HashSet<>(visited))) {
                return true;
            }
        }
        return false;
    }

    private boolean isGroupNode(Map<String, Object> node) {
        String type = StringUtils.defaultString(string(node.get("type"))).trim().toLowerCase();
        return "group".equals(type) || "page-group".equals(type) || "page_group".equals(type)
                || "pagegroup".equals(type) || "menu-group".equals(type) || "menu_group".equals(type)
                || "directory".equals(type) || "folder".equals(type);
    }

    /**
     * 解析页面节点的外部门级 ID（sys_resource.id）。
     * <p>当页面配置了 menuParentId 时，该 ID 指向系统菜单树中的某个目录/菜单，
     * 页面将直接挂载到该目录下而非应用根目录下。返回 null 表示未配置外部门级。</p>
     */
    private String resolveExternalMenuParentId(Map<String, Object> node, String clientCode) {
        String parentKey = "h5".equalsIgnoreCase(clientCode) ? "mobileMenuParentId" : "menuParentId";
        String menuParentId = StringUtils.trimToNull(string(node.get(parentKey)));
        if (menuParentId == null) {
            menuParentId = StringUtils.trimToNull(string(map(node.get("settings")).get(parentKey)));
        }
        return menuParentId;
    }

    /**
     * 解析页面节点的菜单排序：优先读取 menuSort，其次 settings.menuSort，兑底用 sort。
     */
    private Integer resolveMenuSort(Map<String, Object> node) {
        Object menuSort = node.get("menuSort");
        if (menuSort == null) {
            menuSort = map(node.get("settings")).get("menuSort");
        }
        if (menuSort != null) {
            try {
                return Integer.valueOf(String.valueOf(menuSort));
            } catch (Exception ignored) {
                // fall through to default sort
            }
        }
        return integer(node.get("sort"));
    }

    private String permission(String applicationCode, String nodeId) {
        return "ai:business:application:" + applicationCode + ":page:" + nodeId;
    }

    private String resolvePortalIdentifier(Map<String, Object> application, String applicationCode) {
        return StringUtils.defaultIfBlank(string(application.get("portalSlug")), applicationCode);
    }

    private String resolveMenuPath(Map<String, Object> application,
                                   String portalIdentifier, Map<String, Object> node, String nodeId,
                                   boolean directory, String clientCode, List<Map<String, Object>> publishedObjects) {
        if (!"h5".equalsIgnoreCase(clientCode)) {
            if (directory) {
                return "/app/" + portalIdentifier;
            }
            String desktopPagePath = resolveDesktopPagePath(application, node, publishedObjects);
            return StringUtils.defaultIfBlank(desktopPagePath,
                    "/app/" + portalIdentifier + "?pageId=" + nodeId);
        }
        if (directory) {
            return "/app/" + portalIdentifier;
        }
        Map<String, Object> objectRef = map(node.get("objectRef"));
        String configKey = resolveObjectConfigKey(node, objectRef, publishedObjects);
        if (StringUtils.isBlank(configKey)) {
            return "/pages/app-entry";
        }
        String applicationId = StringUtils.trimToNull(string(application.get("id")));
        StringBuilder path = new StringBuilder("/pages/lowcode-runtime?configKey=")
                .append(configKey);
        if (applicationId != null) {
            path.append("&appId=").append(applicationId);
        }
        return path.toString();
    }

    private String resolveDesktopPagePath(Map<String, Object> application, Map<String, Object> node,
                                          List<Map<String, Object>> publishedObjects) {
        Map<String, Object> objectRef = map(node.get("objectRef"));
        String configKey = resolveObjectConfigKey(node, objectRef, publishedObjects);
        if (StringUtils.isBlank(configKey)) {
            return null;
        }
        String applicationId = StringUtils.trimToNull(string(application.get("id")));
        String pageKey = StringUtils.defaultIfBlank(string(node.get("pageKey")),
                string(objectRef.get("pageKey")));
        pageKey = StringUtils.defaultIfBlank(pageKey, "list");
        String pageMode = StringUtils.defaultIfBlank(string(node.get("pageMode")),
                string(objectRef.get("pageMode")));
        pageMode = StringUtils.defaultIfBlank(pageMode, "crud");
        StringBuilder path = new StringBuilder("/ai/crud-page/").append(configKey)
                .append("?pageKey=").append(pageKey);
        if (applicationId != null) {
            path.append("&appId=").append(applicationId);
        }
        String formKey = StringUtils.defaultIfBlank(string(node.get("formKey")),
                string(objectRef.get("formKey")));
        if (StringUtils.isNotBlank(formKey)) {
            path.append("&formKey=").append(formKey);
        }
        if ("form".equalsIgnoreCase(pageMode)) {
            path.append("&runtimeOpenMode=CREATE_FORM&mode=create");
        }
        return path.toString();
    }

    private String resolveObjectConfigKey(Map<String, Object> node, Map<String, Object> objectRef,
                                          List<Map<String, Object>> publishedObjects) {
        String configKey = StringUtils.defaultIfBlank(string(node.get("configKey")),
                string(objectRef.get("configKey")));
        if (StringUtils.isNotBlank(configKey)) {
            return configKey;
        }
        String objectId = StringUtils.trimToNull(string(objectRef.get("objectId")));
        String objectCode = StringUtils.trimToNull(string(objectRef.get("objectCode")));
        return publishedObjects.stream()
                .filter(object -> (objectId != null && objectId.equals(string(object.get("objectId"))))
                        || (objectCode != null && objectCode.equals(string(object.get("objectCode")))))
                .map(object -> StringUtils.trimToNull(string(object.get("configKey"))))
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private String resolveDesktopMenuComponent(String menuPath) {
        return StringUtils.isNotBlank(menuPath) && menuPath.startsWith("/ai/crud-page/")
                ? "ai/crud-page" : PORTAL_COMPONENT;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> raw ? new LinkedHashMap<>((Map<String, Object>) raw) : Map.of(); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Integer integer(Object value) { try { return value == null ? 0 : Integer.valueOf(String.valueOf(value)); } catch (Exception ignored) { return 0; } }
    private List<Map<String, Object>> maps(Object value) { if (!(value instanceof List<?> list)) return List.of(); return list.stream().filter(Map.class::isInstance).map(this::map).toList(); }
}
