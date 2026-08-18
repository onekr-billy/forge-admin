package com.mdframe.forge.plugin.generator.service.businessapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessAppVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationRuntimeVO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从不可变应用版本构建当前用户的正式运行配置。 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationRuntimeService {

    private final BusinessApplicationService applicationService;
    private final BusinessApplicationVersionService versionService;
    private final BusinessApplicationSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    public BusinessApplicationRuntimeVO runtimeByCode(String applicationCode) {
        BusinessApplicationVO current = applicationService.detailByCode(applicationCode);
        return runtime(current);
    }

    /**
     * 按应用主键读取正式运行快照，供带应用入口上下文的低代码运行配置叠加使用。
     */
    public BusinessApplicationRuntimeVO runtimeById(Long applicationId) {
        return runtime(applicationService.detail(applicationId));
    }

    private BusinessApplicationRuntimeVO runtime(BusinessApplicationVO current) {
        if (!Integer.valueOf(1).equals(current.getStatus())) {
            throw new BusinessException("应用已停用，暂时无法访问");
        }
        if (current.getLastPublishVersion() == null) {
            throw new BusinessException("应用尚未发布，请先完成发布后再访问正式运行页");
        }
        AiBusinessApplicationVersion version = versionService.requireVersion(
                current.getId(), current.getLastPublishVersion());
        Map<String, Object> snapshot = snapshotService.parse(version.getSnapshotJson());
        Map<String, Object> applicationSnapshot = map(snapshot.get("application"));
        Map<String, Object> options = map(applicationSnapshot.get("options"));
        options.put("inAppBuilder", filterBuilder(
                map(options.get("inAppBuilder")), current.getApplicationCode(),
                resolvePermissionCodes(), resolveAdminAccess()));
        applicationSnapshot.put("options", options);

        BusinessApplicationRuntimeVO runtime = new BusinessApplicationRuntimeVO();
        runtime.setVersionNo(version.getVersionNo());
        runtime.setApplication(toApplication(applicationSnapshot, current, version.getVersionNo()));
        runtime.setObjects(maps(snapshot.get("objects")).stream().map(this::toObject).toList());
        runtime.setEntries(maps(snapshot.get("entries")).stream().map(this::toEntry).toList());
        return runtime;
    }

    Map<String, Object> filterBuilder(Map<String, Object> builder,
                                      String applicationCode,
                                      Set<String> permissionCodes) {
        return filterBuilder(builder, applicationCode, permissionCodes, false);
    }

    Map<String, Object> filterBuilder(Map<String, Object> builder,
                                      String applicationCode,
                                      Set<String> permissionCodes,
                                      boolean bypassRoleAccess) {
        if (builder.isEmpty()) {
            return builder;
        }
        List<Map<String, Object>> nodes = maps(builder.get("nodes"));
        Map<String, Map<String, Object>> nodesById = new LinkedHashMap<>();
        nodes.forEach(node -> {
            String id = StringUtils.trimToNull(string(node.get("id")));
            if (id != null) {
                nodesById.put(id, node);
            }
        });

        Set<String> accessiblePages = new LinkedHashSet<>();
        nodes.stream()
                .filter(node -> "page".equalsIgnoreCase(string(node.get("type"))))
                .filter(node -> accessAllowed(node, applicationCode, permissionCodes, bypassRoleAccess))
                .filter(node -> ancestorsAllow(node, nodesById, applicationCode, permissionCodes, bypassRoleAccess))
                .map(node -> string(node.get("id")))
                .filter(StringUtils::isNotBlank)
                .forEach(accessiblePages::add);

        Set<String> includedNodes = new LinkedHashSet<>(accessiblePages);
        accessiblePages.forEach(pageId -> includeAncestors(pageId, nodesById, includedNodes));
        List<Map<String, Object>> filteredNodes = new ArrayList<>();
        nodes.stream()
                .filter(node -> includedNodes.contains(string(node.get("id"))))
                .forEach(node -> filteredNodes.add(new LinkedHashMap<>(node)));

        Map<String, Object> pages = map(builder.get("pages"));
        Map<String, Object> filteredPages = new LinkedHashMap<>();
        accessiblePages.forEach(pageId -> {
            if (pages.containsKey(pageId)) {
                filteredPages.put(pageId, pages.get(pageId));
            }
        });

        String homePageId = StringUtils.trimToNull(string(builder.get("homePageId")));
        if (!accessiblePages.contains(homePageId)) {
            homePageId = filteredNodes.stream()
                    .filter(node -> accessiblePages.contains(string(node.get("id"))))
                    .sorted(Comparator.comparingInt(node -> integer(node.get("sort"))))
                    .map(node -> string(node.get("id")))
                    .findFirst().orElse(null);
        }

        Map<String, Object> filtered = new LinkedHashMap<>(builder);
        filtered.put("homePageId", homePageId);
        filtered.put("nodes", filteredNodes);
        filtered.put("pages", filteredPages);
        return filtered;
    }

    private boolean ancestorsAllow(Map<String, Object> node,
                                   Map<String, Map<String, Object>> nodesById,
                                   String applicationCode,
                                   Set<String> permissionCodes,
                                   boolean bypassRoleAccess) {
        Set<String> visited = new HashSet<>();
        String parentId = StringUtils.trimToNull(string(node.get("parentId")));
        while (parentId != null && visited.add(parentId)) {
            Map<String, Object> parent = nodesById.get(parentId);
            if (parent == null || !accessAllowed(parent, applicationCode, permissionCodes, bypassRoleAccess)) {
                return false;
            }
            parentId = StringUtils.trimToNull(string(parent.get("parentId")));
        }
        return parentId == null;
    }

    private void includeAncestors(String nodeId,
                                  Map<String, Map<String, Object>> nodesById,
                                  Set<String> includedNodes) {
        Set<String> visited = new HashSet<>();
        Map<String, Object> node = nodesById.get(nodeId);
        String parentId = node == null ? null : StringUtils.trimToNull(string(node.get("parentId")));
        while (parentId != null && visited.add(parentId)) {
            includedNodes.add(parentId);
            Map<String, Object> parent = nodesById.get(parentId);
            parentId = parent == null ? null : StringUtils.trimToNull(string(parent.get("parentId")));
        }
    }

    private boolean accessAllowed(Map<String, Object> node,
                                  String applicationCode,
                                  Set<String> permissionCodes,
                                  boolean bypassRoleAccess) {
        if (bypassRoleAccess || !systemMenuVisible(node)) {
            return true;
        }
        String nodeId = StringUtils.trimToNull(string(node.get("id")));
        String normalizedApplicationCode = StringUtils.trimToNull(applicationCode);
        if (nodeId == null || normalizedApplicationCode == null) {
            return false;
        }
        String permission = "ai:business:application:" + normalizedApplicationCode + ":page:" + nodeId;
        return permissionCodes.contains(permission)
                || permissionCodes.contains("*:*:*")
                || permissionCodes.contains("**");
    }

    private boolean systemMenuVisible(Map<String, Object> node) {
        Object value = node.get("systemMenuVisible");
        if (value == null) {
            value = map(node.get("settings")).get("systemMenuVisible");
        }
        return Boolean.TRUE.equals(value);
    }

    private BusinessApplicationVO toApplication(Map<String, Object> snapshot,
                                                BusinessApplicationVO current,
                                                Integer versionNo) {
        BusinessApplicationVO application = new BusinessApplicationVO();
        application.setId(longValue(snapshot.get("id")));
        application.setApplicationCode(string(snapshot.get("applicationCode")));
        application.setApplicationName(string(snapshot.get("applicationName")));
        application.setSuiteCode(string(snapshot.get("suiteCode")));
        application.setSuiteName(current.getSuiteName());
        application.setIcon(string(snapshot.get("icon")));
        application.setDescription(string(snapshot.get("description")));
        application.setStatus(integer(snapshot.get("status")));
        application.setDesignStatus(StringUtils.defaultIfBlank(
                string(snapshot.get("designStatus")), "PUBLISHED"));
        application.setLastPublishVersion(versionNo);
        application.setOptions(writeJson(snapshot.get("options")));
        return application;
    }

    private BusinessApplicationObjectVO toObject(Map<String, Object> snapshot) {
        BusinessApplicationObjectVO object = objectMapper.convertValue(
                snapshot, BusinessApplicationObjectVO.class);
        object.setObjectId(longValue(snapshot.get("objectId")));
        return object;
    }

    private BusinessAppVO toEntry(Map<String, Object> snapshot) {
        Map<String, Object> normalized = new LinkedHashMap<>(snapshot);
        Object options = normalized.remove("options");
        BusinessAppVO entry = objectMapper.convertValue(normalized, BusinessAppVO.class);
        entry.setOptions(writeJson(options));
        return entry;
    }

    private Set<String> resolvePermissionCodes() {
        try {
            var loginUser = SessionHelper.getLoginUser();
            return loginUser == null || loginUser.getPermissions() == null
                    ? Set.of() : Set.copyOf(loginUser.getPermissions());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean resolveAdminAccess() {
        try {
            return SessionHelper.isAdmin();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("应用运行配置序列化失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw) : new LinkedHashMap<>();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        collection.stream().filter(Map.class::isInstance).map(this::map).forEach(result::add);
        return result;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        try {
            return value == null ? 0 : Integer.valueOf(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Long longValue(Object value) {
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
