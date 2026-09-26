package com.mdframe.forge.plugin.generator.service.businessapp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessObject;
import com.mdframe.forge.plugin.generator.mapper.BusinessObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.appendSchemaChildTableFields;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.buildFieldPreview;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowFormFieldCatalog.collectBusinessFormFieldCatalog;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readMapList;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedArray;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.readNestedObject;
import static com.mdframe.forge.plugin.generator.service.businessapp.BusinessFlowJsonReader.textValue;

/**
 * 应用页面表单资产解析器。
 * <p>
 * 使用 Resolver + Cache-Aside 将稳定 formKey 解析回应用草稿中的页面和表单资产；
 * 返回缓存副本，不修改应用设计快照。
 */
@Slf4j
final class BusinessFlowApplicationPageFormResolver {

    private static final long CACHE_TTL_MS = 300_000L;

    private final Supplier<BusinessApplicationService> applicationServiceSupplier;
    private final BusinessObjectMapper businessObjectMapper;
    private final Supplier<Long> tenantIdSupplier;
    private final BiConsumer<String, Long> timingMarker;
    private final Consumer<String> note;
    private final ConcurrentHashMap<String, CachedJsonValue> pageAssetCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CachedJsonValue> builderCache = new ConcurrentHashMap<>();

    BusinessFlowApplicationPageFormResolver(Supplier<BusinessApplicationService> applicationServiceSupplier,
                                            BusinessObjectMapper businessObjectMapper,
                                            Supplier<Long> tenantIdSupplier,
                                            BiConsumer<String, Long> timingMarker,
                                            Consumer<String> note) {
        this.applicationServiceSupplier = applicationServiceSupplier;
        this.businessObjectMapper = businessObjectMapper;
        this.tenantIdSupplier = tenantIdSupplier;
        this.timingMarker = timingMarker;
        this.note = note;
    }

    private BusinessApplicationService applicationService() {
        return applicationServiceSupplier.get();
    }

    Map<String, Object> collectApplicationPageFormAssets(Long applicationId, String objectCode) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (applicationId == null || applicationId <= 0 || applicationService() == null) {
            return result;
        }
        try {
            var application = applicationService().detail(applicationId);
            JSONObject options = readJsonObject(application.getOptions());
            JSONObject builder = readNestedObject(options.get("inAppBuilder"));
            JSONArray nodes = readNestedArray(builder.get("nodes"));
            JSONObject pages = readNestedObject(builder.get("pages"));
            JSONArray assets = readNestedArray(builder.get("formAssets"));
            if (nodes.isEmpty() || pages.isEmpty() || assets.isEmpty()) {
                return result;
            }
            Map<String, JSONObject> assetsById = new LinkedHashMap<>();
            for (int i = 0; i < assets.size(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset != null && StringUtils.isNotBlank(asset.getString("id"))) {
                    assetsById.put(asset.getString("id"), asset);
                }
            }
            List<Map<String, Object>> formAssets = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                if (node == null || !"page".equalsIgnoreCase(node.getString("type"))) {
                    continue;
                }
                JSONObject objectRef = readNestedObject(node.get("objectRef"));
                String pageObjectCode = StringUtils.firstNonBlank(
                        objectRef.getString("objectCode"), node.getString("objectCode"));
                if (!matchesApplicationObject(applicationId, objectCode, pageObjectCode, objectRef)) {
                    continue;
                }
                String pageId = StringUtils.trimToNull(node.getString("id"));
                if (pageId == null) {
                    continue;
                }
                JSONObject page = pages.getJSONObject(pageId);
                if (page == null) {
                    continue;
                }
                Set<String> referencedAssetIds = new LinkedHashSet<>();
                collectFormAssetIds(page, referencedAssetIds);
                String directAssetId = StringUtils.firstNonBlank(
                        node.getString("formAssetId"), objectRef.getString("formAssetId"));
                if (directAssetId != null) {
                    referencedAssetIds.add(directAssetId);
                }
                // Older object pages were persisted as a CRUD block without a
                // formAssetId on the block.  The application still owns the
                // form asset in inAppBuilder.formAssets, and when there is one
                // unambiguous asset it is the page's default task form.  Keep
                // this fallback here so historical pages can participate in
                // business-process task forms without asking users to rebind
                // the page manually.
                if (referencedAssetIds.isEmpty()) {
                    String defaultAssetId = resolveDefaultPageFormAssetId(
                            node, objectRef, assets);
                    if (defaultAssetId != null) {
                        referencedAssetIds.add(defaultAssetId);
                    }
                }
                for (String assetId : referencedAssetIds) {
                    JSONObject source = assetsById.get(assetId);
                    if (source == null) {
                        continue;
                    }
                    Map<String, Object> item = buildApplicationPageFormAsset(
                            applicationId, objectCode, node, objectRef, source, pageId);
                    String formKey = StringUtils.trimToNull(textValue(item.get("formKey")));
                    if (formKey != null && seen.add(formKey)) {
                        formAssets.add(item);
                    }
                }
            }
            if (formAssets.isEmpty()) {
                return result;
            }
            result.put("applicationId", String.valueOf(applicationId));
            result.put("objectCode", objectCode);
            result.put("formAssets", formAssets);
            result.put("warnings", List.of());
        } catch (Exception error) {
            log.debug("读取应用页面表单资产失败: applicationId={}, objectCode={}", applicationId, objectCode, error);
        }
        return result;
    }

    /**
     * 兼容业务对象编码唯一化前保存的页面引用。页面仍携带稳定 configKey 时，
     * 即使页面上的 objectCode 是旧编码，也应通过 configKey 解析到规范编码；
     * 解析结果必须与请求对象是同一个对象，否则会把应用内其它业务对象的
     * 页面表单误纳入当前对象的候选任务表单。
     */
    private boolean matchesApplicationObject(Long applicationId,
                                             String requestedObjectCode,
                                             String pageObjectCode,
                                             JSONObject objectRef) {
        if (StringUtils.equals(requestedObjectCode, pageObjectCode)) {
            return true;
        }
        if (applicationId == null || StringUtils.isBlank(requestedObjectCode) || objectRef == null) {
            return false;
        }
        String configKey = StringUtils.trimToNull(objectRef.getString("configKey"));
        if (configKey == null) {
            return false;
        }
        AiBusinessObject canonical = businessObjectMapper.selectByConfigKey(tenantIdSupplier.get(), configKey);
        return canonical != null
                && StringUtils.equals(canonical.getObjectCode(), requestedObjectCode);
    }

    private String resolveDefaultPageFormAssetId(JSONObject pageNode,
                                                  JSONObject objectRef,
                                                  JSONArray assets) {
        if (assets == null || assets.isEmpty()) {
            return null;
        }
        String requestedFormKey = StringUtils.firstNonBlank(
                pageNode == null ? null : pageNode.getString("formKey"),
                pageNode == null ? null : pageNode.getString("defaultFormKey"),
                objectRef == null ? null : objectRef.getString("formKey"),
                objectRef == null ? null : objectRef.getString("defaultFormKey"));
        if (requestedFormKey != null) {
            for (int i = 0; i < assets.size(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                if (asset != null && StringUtils.equals(requestedFormKey,
                        StringUtils.firstNonBlank(asset.getString("formKey"), asset.getString("id")))) {
                    return StringUtils.trimToNull(asset.getString("id"));
                }
            }
        }
        String markedDefault = null;
        String onlyAsset = null;
        int assetCount = 0;
        for (int i = 0; i < assets.size(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (asset == null || StringUtils.isBlank(asset.getString("id"))) {
                continue;
            }
            assetCount++;
            onlyAsset = asset.getString("id");
            if (Boolean.TRUE.equals(asset.getBoolean("default"))
                    || Boolean.TRUE.equals(asset.getBoolean("isDefault"))) {
                markedDefault = asset.getString("id");
            }
        }
        if (markedDefault != null) {
            return markedDefault;
        }
        return assetCount == 1 ? onlyAsset : null;
    }

    private Map<String, Object> buildApplicationPageFormAsset(Long applicationId,
                                                               String canonicalObjectCode,
                                                               JSONObject pageNode,
                                                               JSONObject objectRef,
                                                               JSONObject source,
                                                               String pageId) {
        Map<String, Object> item = new LinkedHashMap<>();
        String sourceAssetId = StringUtils.defaultIfBlank(source.getString("id"), "default");
        String formKey = "app_" + applicationId + "_page_" + pageId + "_form_" + sourceAssetId;
        String pageName = StringUtils.firstNonBlank(
                pageNode.getString("pageName"),
                pageNode.getString("name"),
                pageNode.getString("title"),
                pageId);
        String objectName = StringUtils.firstNonBlank(
                objectRef.getString("objectName"), pageName, objectRef.getString("objectCode"));
        JSONObject formDesignerSchema = readNestedObject(source.get("formDesignerSchema"));
        JSONObject schema = readNestedObject(source.get("schema"));
        if (schema.isEmpty()) {
            schema = formDesignerSchema;
        }
        List<Map<String, Object>> fields = readMapList(readNestedArray(source.get("fieldCatalog")));
        if (fields.isEmpty()) {
            fields = readMapList(readNestedArray(source.get("fields")));
        }
        if (fields.isEmpty()) {
            fields = collectBusinessFormFieldCatalog(schema);
        }
        fields = new ArrayList<>(fields);
        appendSchemaChildTableFields(schema, fields);
        // 子表字段由持有 runtimeConfig/options 的调用方补齐；这里再查 published config 会把大 JSON 再拉一遍
        item.put("type", "BUSINESS_OBJECT_FORM");
        item.put("formMode", "BUSINESS_OBJECT_FORM");
        item.put("applicationId", String.valueOf(applicationId));
        item.put("objectId", objectRef.getString("objectId"));
        item.put("objectCode", StringUtils.firstNonBlank(
                canonicalObjectCode, objectRef.getString("objectCode")));
        item.put("objectName", objectName);
        item.put("configKey", objectRef.getString("configKey"));
        item.put("formKey", formKey);
        item.put("sourceFormKey", source.getString("formKey"));
        item.put("formName", StringUtils.firstNonBlank(
                source.getString("formName"), source.getString("name"), pageName, objectName + "表单"));
        item.put("pageId", pageId);
        item.put("pageCode", StringUtils.firstNonBlank(pageNode.getString("pageCode"), pageNode.getString("pageKey"), pageId));
        item.put("pageName", pageName);
        item.put("pageType", pageNode.getString("pageType"));
        item.put("source", "applicationPage");
        item.put("sourceType", "applicationPageForm");
        item.put("fieldCatalog", fields);
        item.put("fields", fields);
        item.put("fieldCount", fields.size());
        item.put("fieldPreview", buildFieldPreview(fields));
        item.put("supportsSave", true);
        schema.put("formKey", formKey);
        schema.put("formName", item.get("formName"));
        schema.put("fieldCatalog", fields);
        schema.put("fields", fields);
        item.put("schema", schema);
        return item;
    }

    private void collectFormAssetIds(Object value, Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            Object propsValue = map.get("props");
            if (propsValue instanceof Map<?, ?> props && props.get("formAssetId") != null) {
                String id = StringUtils.trimToNull(String.valueOf(props.get("formAssetId")));
                if (id != null) {
                    result.add(id);
                }
            }
            map.values().forEach(child -> collectFormAssetIds(child, result));
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectFormAssetIds(child, result));
        }
    }

    /**
     * 查询代码应用配置化元数据。未配置时返回空 Map，调用方继续使用 Provider 默认资产。
     */

    JSONObject slimPageFormAssetMeta(JSONObject asset) {
        if (asset == null || asset.isEmpty()) {
            return new JSONObject();
        }
        JSONObject meta = new JSONObject();
        for (String key : List.of(
                "id", "formKey", "formName", "formMode", "providerKey", "formUrl", "viewKey",
                "objectCode", "objectId", "configKey", "applicationId", "pageId", "pageCode", "pageName")) {
            Object value = asset.get(key);
            if (value != null) {
                meta.put(key, value);
            }
        }
        return meta;
    }

    JSONObject resolveApplicationPageFormAsset(String formKey) {
        String key = StringUtils.trimToNull(formKey);
        if (key == null || applicationService() == null || !key.startsWith("app_")) {
            return new JSONObject();
        }
        String cacheKey = tenantIdSupplier.get() + ":" + key;
        CachedJsonValue cached = pageAssetCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            note.accept(cached.isEmpty() ? "pageAssetCache=hitEmpty" : "pageAssetCache=hit");
            return cached.copy();
        }
        ParsedApplicationPageFormKey parsed = parseApplicationPageFormKey(key);
        if (parsed == null) {
            note.accept("pageAsset=badFormKey");
            return new JSONObject();
        }
        try {
            long mark = System.nanoTime();
            JSONObject builder = loadCachedInAppBuilder(parsed.applicationId());
            timingMarker.accept("inAppBuilderMs", mark);
            JSONArray nodes = readNestedArray(builder.get("nodes"));
            JSONObject pages = readNestedObject(builder.get("pages"));
            JSONArray assets = readNestedArray(builder.get("formAssets"));
            if (nodes.isEmpty() && pages.isEmpty() && assets.isEmpty()) {
                note.accept("pageAsset=emptyBuilder nodes=" + nodes.size()
                        + " pages=" + pages.size() + " assets=" + assets.size());
                // 空结果不长缓存，避免 CAST/解析异常把 missEmpty 锁死 300s
                return new JSONObject();
            }

            // 直接按 formKey 拆出的 pageId/assetId 定位，不再依赖页面区块引用链（引用链缺失是 missEmpty 主因）
            JSONObject pageNode = findApplicationPageNode(nodes, parsed.pageId());
            JSONObject page = pages.getJSONObject(parsed.pageId());
            JSONObject source = findApplicationFormAsset(assets, parsed.assetId());
            if (source == null) {
                note.accept("pageAsset=assetNotFound assetId=" + parsed.assetId()
                        + " assets=" + assets.size());
                return new JSONObject();
            }
            if (pageNode == null && page == null) {
                note.accept("pageAsset=pageNotFound pageId=" + parsed.pageId()
                        + " nodes=" + nodes.size());
                return new JSONObject();
            }
            if (pageNode == null) {
                pageNode = new JSONObject();
                pageNode.put("id", parsed.pageId());
                pageNode.put("type", "page");
            }
            JSONObject objectRef = readNestedObject(pageNode.get("objectRef"));
            JSONObject resolved = readNestedObject(buildApplicationPageFormAsset(
                    parsed.applicationId(),
                    StringUtils.firstNonBlank(
                            objectRef.getString("objectCode"), objectRef.getString("configKey")),
                    pageNode, objectRef, source, parsed.pageId()));
            pageAssetCache.put(cacheKey, CachedJsonValue.of(resolved));
            note.accept("pageAssetCache=miss");
            return resolved;
        } catch (Exception error) {
            log.debug("解析应用页面表单资产失败: formKey={}", formKey, error);
            note.accept("pageAsset=error:" + error.getClass().getSimpleName());
            return new JSONObject();
        }
    }

    private ParsedApplicationPageFormKey parseApplicationPageFormKey(String formKey) {
        String key = StringUtils.trimToNull(formKey);
        if (key == null || !key.startsWith("app_")) {
            return null;
        }
        int pageMarker = key.indexOf("_page_");
        if (pageMarker <= 4) {
            return null;
        }
        Long applicationId;
        try {
            applicationId = Long.valueOf(key.substring(4, pageMarker));
        } catch (NumberFormatException error) {
            return null;
        }
        String remainder = key.substring(pageMarker + "_page_".length());
        int formMarker = remainder.indexOf("_form_");
        if (formMarker <= 0) {
            return null;
        }
        String pageId = StringUtils.trimToNull(remainder.substring(0, formMarker));
        String assetId = StringUtils.trimToNull(remainder.substring(formMarker + "_form_".length()));
        if (pageId == null || assetId == null) {
            return null;
        }
        return new ParsedApplicationPageFormKey(applicationId, pageId, assetId);
    }

    private JSONObject findApplicationPageNode(JSONArray nodes, String pageId) {
        if (nodes == null || nodes.isEmpty() || StringUtils.isBlank(pageId)) {
            return null;
        }
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject pageNode = nodes.getJSONObject(i);
            if (pageNode == null) {
                continue;
            }
            if (StringUtils.equals(pageId, StringUtils.trimToNull(pageNode.getString("id")))) {
                return pageNode;
            }
        }
        return null;
    }

    private JSONObject findApplicationFormAsset(JSONArray assets, String assetId) {
        if (assets == null || assets.isEmpty() || StringUtils.isBlank(assetId)) {
            return null;
        }
        for (int i = 0; i < assets.size(); i++) {
            JSONObject source = assets.getJSONObject(i);
            if (source == null) {
                continue;
            }
            if (StringUtils.equals(assetId, StringUtils.trimToNull(source.getString("id")))
                    || StringUtils.equals(assetId, StringUtils.trimToNull(source.getString("formKey")))) {
                return source;
            }
        }
        return null;
    }

    private record ParsedApplicationPageFormKey(Long applicationId, String pageId, String assetId) {
    }

    private JSONObject loadCachedInAppBuilder(Long applicationId) {
        if (applicationId == null || applicationService() == null) {
            return new JSONObject();
        }
        CachedJsonValue cached = builderCache.get(applicationId);
        if (cached != null && !cached.expired()) {
            note.accept("inAppBuilderCache=hit");
            return cached.copy();
        }
        JSONObject builder = applicationService().loadInAppBuilder(applicationId);
        builderCache.put(applicationId,
                CachedJsonValue.of(builder == null ? new JSONObject() : builder));
        note.accept("inAppBuilderCache=miss db:ai_business_application.inAppBuilder");
        return builder == null ? new JSONObject() : builder;
    }

    JSONObject resolveApplicationPageFormSchema(String formKey) {
        JSONObject asset = resolveApplicationPageFormAsset(formKey);
        if (asset.isEmpty()) {
            return new JSONObject();
        }
        JSONObject schema = readNestedObject(asset.get("schema"));
        if (schema.isEmpty()) {
            schema.put("formKey", asset.getString("formKey"));
            schema.put("formName", asset.getString("formName"));
            schema.put("fieldCatalog", asset.get("fieldCatalog"));
            schema.put("fields", asset.get("fields"));
        }
        return schema;
    }


    private JSONObject readJsonObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static final class CachedJsonValue {
        private final String payload;
        private final long expireAtMs;
        private final boolean empty;
        private volatile JSONObject parsed;

        private CachedJsonValue(String payload, long expireAtMs, boolean empty) {
            this.payload = payload;
            this.expireAtMs = expireAtMs;
            this.empty = empty;
        }

        static CachedJsonValue of(JSONObject value) {
            JSONObject source = value == null ? new JSONObject() : value;
            CachedJsonValue cached = new CachedJsonValue(
                    source.toJSONString(),
                    System.currentTimeMillis() + CACHE_TTL_MS,
                    source.isEmpty());
            cached.parsed = source;
            return cached;
        }

        boolean expired() {
            return System.currentTimeMillis() >= expireAtMs;
        }

        boolean isEmpty() {
            return empty;
        }

        JSONObject copy() {
            JSONObject local = parsed;
            if (local == null) {
                local = JSON.parseObject(payload);
                parsed = local;
            }
            return new JSONObject(local);
        }
    }
}
