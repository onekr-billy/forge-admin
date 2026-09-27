package com.mdframe.forge.plugin.generator.codegen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.domain.entity.AiPageTemplate;
import com.mdframe.forge.plugin.generator.domain.entity.GenTable;
import com.mdframe.forge.plugin.generator.domain.entity.GenTableColumn;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeModelSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageSchema;
import com.mdframe.forge.plugin.generator.mapper.GenTableColumnMapper;
import com.mdframe.forge.plugin.generator.service.lowcode.GeneratedLowcodeRuntimeConfigBuilder;
import com.mdframe.forge.plugin.generator.service.lowcode.LowcodeProtocolSnapshotBuilder;
import com.mdframe.forge.plugin.generator.util.GenUtils;
import com.mdframe.forge.plugin.generator.util.LowcodeCodegenOptionUtils;
import com.mdframe.forge.plugin.generator.util.VelocityUtils;
import com.mdframe.forge.starter.core.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Velocity 模板的代码生成策略（适用于简单 CRUD 等结构化组件）
 * <p>
 * 生成内容：
 * - 后端：Entity / Mapper / Mapper.xml / Service / ServiceImpl / Controller / DTO / Query
 * - SQL：menu.sql / dict.sql
 * - 前端：index.vue / api.js（使用业务路由前缀，非 /ai/crud 通用路由）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityCodegenStrategy implements CodegenStrategy {

    private final GenTableColumnMapper genTableColumnMapper;
    private final ObjectMapper objectMapper;
    private final GeneratedLowcodeRuntimeConfigBuilder generatedRuntimeConfigBuilder;
    private final LowcodeProtocolSnapshotBuilder protocolSnapshotBuilder;
    private final ObjectProvider<LowcodeStaticCodegenContributor> staticCodegenContributors;

    /** 默认包名 */
    private static final String DEFAULT_PACKAGE = "com.mdframe.forge";
    /** 默认作者 */
    private static final String DEFAULT_AUTHOR = "Forge Generator";
    /** 生成产物中不允许残留的 Velocity 引用。 */
    private static final Pattern UNRESOLVED_VELOCITY_REFERENCE = Pattern.compile(
            "\\$\\{(?:packageName|moduleName|className|classname|businessName|functionName|author|date|datetime|"
                    + "tableName|businessApiBase|apiBase|configKey|runtimeConfigKey|componentName|apiVarName|pkColumn|"
                    + "entityTreeChildrenField|tree|child|table|column)[^}\\r\\n]*}|"
                    + "\\$(?:pkColumn|tree|child|table|column)\\.[A-Za-z_]");

    @Override
    public boolean supports(String codegenType) {
        // TEMPLATE 类型，或 null/空（向前兼容，默认走模板生成）
        return codegenType == null || "TEMPLATE".equalsIgnoreCase(codegenType);
    }

    @Override
    public Map<String, String> generate(AiCrudConfig config, AiPageTemplate template) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();

        // ── 1. 解析 apiConfig，提取业务路由前缀 ──────────────────────────────
        String outputConfigKey = config.getConfigKey();
        String apiBase = resolveApiBase(config);          // e.g. /order/manage
        AiCrudConfig codegenConfig = generatedRuntimeConfigBuilder.build(config, apiBase);
        String runtimeConfigKey = codegenConfig.getConfigKey();
        String moduleName = StringUtils.defaultIfBlank(readOption(codegenConfig, "moduleName", null),
                resolveModuleName(apiBase));   // e.g. order
        String businessPath = resolveBusinessPath(apiBase); // e.g. manage

        // ── 2. 构造 GenTable（复用已有的 VelocityUtils 体系）────────────────
        GenTable genTable = buildGenTable(codegenConfig, moduleName, businessPath);

        // ── 3. 从数据库加载字段元数据 ─────────────────────────────────────────
        List<GenTableColumn> columns = loadColumns(codegenConfig);
        genTable.setColumns(columns);
        genTable.setPkColumn(GenUtils.getPkColumn(columns));

        // ── 4. 解析四类安全配置，注入注解控制变量 ────────────────────────────
        Map<String, Object> annotationFlags = resolveAnnotationFlags(codegenConfig, columns);
        LowcodePageSchema pageSchema = parsePageSchema(codegenConfig);
        String layoutType = StringUtils.firstNonBlank(
                pageSchema == null ? null : pageSchema.getLayoutType(), codegenConfig.getLayoutType(), "simple-crud");
        Map<String, Object> runtimeOptions = parseJsonObject(codegenConfig.getOptions());
        Map<String, Object> treeConfig = readNestedMap(runtimeOptions, "treeConfig");
        Map<String, Object> masterDetailConfig = readNestedMap(runtimeOptions, "masterDetailConfig");
        List<RelatedTableMeta> relatedTables = buildRelatedTables(
                codegenConfig, pageSchema, genTable, moduleName, businessPath);
        TreeCodegenMeta treeMeta = buildTreeMeta(genTable, relatedTables, treeConfig);
        List<RelatedTableMeta> masterDetailChildren = buildMasterDetailChildren(
                masterDetailConfig, relatedTables, genTable, pageSchema);
        if ("master-detail-crud".equals(layoutType) && masterDetailChildren.isEmpty()) {
            throw new BusinessException("主子表代码生成失败：未解析到有效子表关系，请检查子表及主外键配置");
        }
        boolean hasTreeConfig = treeMeta != null;
        boolean isLeftTreeLayout = hasTreeConfig && "tree-crud".equals(layoutType);
        boolean isTreeTableLayout = hasTreeConfig && !"tree-crud".equals(layoutType);
        boolean isMasterDetailLayout = "master-detail-crud".equals(layoutType) && !masterDetailChildren.isEmpty();
        List<RelatedTableMeta> injectedRelatedTables = resolveInjectedRelatedTables(treeMeta, relatedTables, masterDetailChildren);

        // ── 5. 初始化 Velocity ────────────────────────────────────────────────
        VelocityUtils.initVelocity();
        VelocityContext ctx = VelocityUtils.prepareContext(genTable);

        // 注入注解控制变量
        annotationFlags.forEach(ctx::put);

        // 注入业务路由前缀（前端模板使用）
        ctx.put("apiBase", apiBase);
        ctx.put("businessApiBase", apiBase);
        ctx.put("date", LocalDate.now().toString());

        // 前端 configKey → 组件名/变量名
        String configKey = outputConfigKey;
        String viewPath = configKey.replace("_", "/");
        ctx.put("configKey", configKey);
        ctx.put("runtimeConfigKey", runtimeConfigKey);
        ctx.put("viewPath", viewPath);
        ctx.put("componentName", toPascalCase(configKey));
        ctx.put("apiVarName", toPascalCase(configKey));
        ctx.put("tableComment", StringUtils.isNotBlank(codegenConfig.getTableComment())
                ? codegenConfig.getTableComment() : configKey);

        // searchSchema / columnsSchema / editSchema / apiConfig（前端模板需要）
        Map<String, Object> transConfig = parseJsonObject(codegenConfig.getTransConfig());
        ctx.put("searchSchema", parseJsonArray(codegenConfig.getSearchSchema()));
        ctx.put("columnsSchema", preprocessColumnsSchema(
                parseJsonArray(codegenConfig.getColumnsSchema()), transConfig));
        ctx.put("editSchema", parseJsonArray(codegenConfig.getEditSchema()));
        ctx.put("apiConfig", parseJsonObject(codegenConfig.getApiConfig()));
        ctx.put("layoutType", layoutType);
        ctx.put("options", runtimeOptions);
        ctx.put("optionsJson", toJsonLiteral(runtimeOptions));
        ctx.put("searchSchemaJson", toJsonLiteral(ctx.get("searchSchema")));
        ctx.put("columnsSchemaJson", toJsonLiteral(ctx.get("columnsSchema")));
        ctx.put("editSchemaJson", toJsonLiteral(ctx.get("editSchema")));
        ctx.put("treeConfigJson", toJsonLiteral(treeConfig));
        ctx.put("masterDetailConfigJson", toJsonLiteral(masterDetailConfig));
        ctx.put("hasTreeConfig", hasTreeConfig);
        ctx.put("tree", treeMeta);
        ctx.put("isLeftTreeLayout", isLeftTreeLayout);
        ctx.put("isTreeTableLayout", isTreeTableLayout);
        ctx.put("isMasterDetailLayout", isMasterDetailLayout);
        ctx.put("masterDetailChildren", masterDetailChildren);
        ctx.put("injectedRelatedTables", injectedRelatedTables);
        ctx.put("hasEntityTreeFields", hasTreeConfig && !treeMeta.isSeparateSource());
        ctx.put("entityTreeChildrenField", hasTreeConfig && !treeMeta.isSeparateSource()
                ? treeMeta.getChildrenField() : "children");
        ctx.put("isPrimaryCodegenTable", true);
        ctx.put("hasLogicDeleteColumn", hasColumn(columns, "del_flag"));
        ctx.put("enableServiceExtensions", true);
        boolean includeBackend = readBooleanOption(codegenConfig, "includeBackend", true);
        boolean includeFrontend = readBooleanOption(codegenConfig, "includeFrontend", true);
        String backendBasePath = LowcodeCodegenOptionUtils.normalizeOutputPath(
                readOption(codegenConfig, "backendBasePath", null),
                LowcodeCodegenOptionUtils.DEFAULT_BACKEND_BASE_PATH, "后端 Java 输出路径");
        String mapperXmlBasePath = LowcodeCodegenOptionUtils.normalizeOutputPath(
                readOption(codegenConfig, "mapperXmlBasePath", null),
                LowcodeCodegenOptionUtils.DEFAULT_MAPPER_XML_BASE_PATH, "Mapper XML 输出路径");
        String frontendBasePath = LowcodeCodegenOptionUtils.normalizeOutputPath(
                readOption(codegenConfig, "frontendBasePath", null),
                LowcodeCodegenOptionUtils.DEFAULT_FRONTEND_BASE_PATH, "前端页面输出路径");
        String frontendApiBasePath = LowcodeCodegenOptionUtils.normalizeOutputPath(
                readOption(codegenConfig, "frontendApiBasePath", null),
                LowcodeCodegenOptionUtils.DEFAULT_FRONTEND_API_BASE_PATH, "前端 API 输出路径");
        ctx.put("backendBasePath", backendBasePath);
        ctx.put("mapperXmlBasePath", mapperXmlBasePath);
        ctx.put("frontendBasePath", frontendBasePath);
        ctx.put("frontendApiBasePath", frontendApiBasePath);
        ctx.put("includeBackend", includeBackend);
        ctx.put("includeFrontend", includeFrontend);
        ctx.put("entityPrefix", LowcodeCodegenOptionUtils.normalizeEntityPrefix(
                readOption(codegenConfig, "entityPrefix", "")));
        ctx.put("stripTablePrefixes", resolveStripTablePrefixes(codegenConfig));
        List<LowcodeStaticCodegenContributor> activeContributors = includeBackend
                ? staticCodegenContributors.orderedStream()
                .filter(contributor -> contributor.supports(codegenConfig))
                .toList()
                : List.of();
        for (LowcodeStaticCodegenContributor contributor : activeContributors) {
            contributor.contributeContext(codegenConfig, ctx);
        }
        ctx.put("staticCodegenContributors", activeContributors.stream()
                .map(LowcodeStaticCodegenContributor::capabilityId)
                .toList());

        // ── 6. 渲染后端代码 ───────────────────────────────────────────────────
        String className = genTable.getClassName();
        String pkgPath = genTable.getPackageName().replace(".", "/") + "/" + moduleName + "/";
        String javaRoot = backendBasePath + "/" + pkgPath;
        String mapperXmlRoot = mapperXmlBasePath + "/";
        String extensionSamplePath = null;
        if (includeBackend) {
            renderTo(files, "templates/vm/entity.java.vm",      ctx, javaRoot + "entity/" + className + ".java");
            renderTo(files, "templates/vm/mapper.java.vm",      ctx, javaRoot + "mapper/" + className + "Mapper.java");
            renderTo(files, "templates/vm/mapper.xml.vm",       ctx, mapperXmlRoot + className + "Mapper.xml");
            renderTo(files, "templates/vm/service.java.vm",     ctx, javaRoot + "service/I" + className + "Service.java");
            renderTo(files, "templates/vm/serviceImpl.java.vm", ctx, javaRoot + "service/impl/" + className + "ServiceImpl.java");
            renderTo(files, "templates/vm/serviceExtension.java.vm", ctx,
                    javaRoot + "service/extension/" + className + "ServiceExtension.java");
            renderTo(files, "templates/vm/controller.java.vm",  ctx, javaRoot + "controller/" + className + "Controller.java");
            renderTo(files, "templates/vm/dto.java.vm",         ctx, javaRoot + "dto/" + className + "DTO.java");
            renderTo(files, "templates/vm/query.java.vm",       ctx, javaRoot + "dto/" + className + "Query.java");
            if (isMasterDetailLayout) {
                renderTo(files, "templates/vm/masterDetailDTO.java.vm", ctx,
                        javaRoot + "dto/" + className + "MasterDetailDTO.java");
            }
            renderRelatedTableFiles(files, relatedTables, javaRoot, mapperXmlRoot, treeMeta);
            extensionSamplePath = "examples/" + javaRoot
                    + "service/extension/custom/" + className + "BusinessExtension.java.example";
            renderTo(files, "templates/vm/businessExtension.java.vm.example", ctx, extensionSamplePath);
        }

        // ── 7. 渲染 SQL ───────────────────────────────────────────────────────
        boolean includeSql = readBooleanOption(codegenConfig, "includeSql", true);
        boolean includeMenuSql = readBooleanOption(codegenConfig, "includeMenuSql", true);
        boolean includeDictSql = readBooleanOption(codegenConfig, "includeDictSql", true);
        boolean includeExcelSql = readBooleanOption(codegenConfig, "includeExcelSql", true);
        ctx.put("includeSql", includeSql);
        ctx.put("includeMenuSql", includeMenuSql);
        ctx.put("includeDictSql", includeDictSql);
        ctx.put("includeExcelSql", includeExcelSql);
        if (includeSql) {
            renderTo(files, "templates/vm/sql/table.sql.vm", ctx,
                    "sql/schema/" + codegenConfig.getTableName() + ".sql");
            renderRelatedTableSql(files, relatedTables, treeMeta);
        }
        if (includeSql && includeMenuSql) {
            renderTo(files, "templates/vm/sql/menu.sql.vm", ctx,
                    "sql/" + codegenConfig.getTableName() + "_menu.sql");
        }
        if (includeSql && includeDictSql && (boolean) annotationFlags.getOrDefault("hasDictConfig", false)) {
            renderTo(files, "templates/vm/sql/dict.sql.vm", ctx,
                    "sql/" + codegenConfig.getTableName() + "_dict.sql");
        }
        if (includeSql && includeExcelSql) {
            renderTo(files, "templates/vm/sql/excel.sql.vm", ctx,
                    "sql/" + codegenConfig.getTableName() + "_excel.sql");
        }

        // ── 8. 渲染前端代码 ───────────────────────────────────────────────────
        if (includeFrontend) {
            renderTo(files, "templates/vm/ai-crud/index.vue.vm", ctx,
                    frontendBasePath + "/" + viewPath + "/index.vue");
            renderTo(files, "templates/vm/ai-crud/api.js.vm", ctx,
                    frontendApiBasePath + "/" + configKey + ".js");
        }
        if (includeBackend) {
            contributeStaticCodegenFiles(files, codegenConfig, ctx, activeContributors);
        }

        // ── 9. 附带完整协议、静态编译覆盖报告与文件所有权 ─────────────────────
        LowcodeProtocolSnapshotBuilder.ProtocolArtifacts artifacts = protocolSnapshotBuilder.build(codegenConfig);
        if (includeFrontend) {
            files.put(frontendBasePath + "/" + viewPath + "/runtime-config.json",
                    artifacts.frontendRuntimeConfig());
        }
        files.put("config/" + runtimeConfigKey + "-config.json", artifacts.frontendRuntimeConfig());
        files.put("config/" + runtimeConfigKey + "-protocol.json", artifacts.protocolSnapshot());
        files.put("config/" + runtimeConfigKey + "-coverage.json", artifacts.coverageReport());
        var printing = objectMapper.readTree(artifacts.frontendRuntimeConfig()).path("printing");
        files.put("config/" + runtimeConfigKey + "-printing.json",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(printing));
        renderTo(files, "templates/vm/PRINTING.md.vm", ctx, "PRINTING.md");
        renderTo(files, "templates/vm/README.md.vm", ctx, "README.md");
        String ownershipPath = "config/" + runtimeConfigKey + "-ownership.json";
        files.put(ownershipPath, buildOwnershipManifest(
                files.keySet(), extensionSamplePath, ownershipPath,
                includeBackend ? javaRoot + "service/extension/custom/**/*.java" : null));

        return files;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 业务路由解析
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * 从 apiConfig.list 提取业务路由前缀
     * <p>
     * 规则：取 URL 中去掉 /page 之前的部分作为 BASE。
     * 例：get@/order/manage/page → /order/manage
     * 例：get@/ai/crud/{configKey}/page → 降级为 /{configKey}
     */
    private String resolveApiBase(AiCrudConfig config) {
        try {
            String businessApiBase = readOption(config, "businessApiBase", null);
            if (StringUtils.isNotBlank(businessApiBase)) {
                return normalizeApiBase(businessApiBase);
            }
            Map<String, Object> apiConf = parseJsonObject(config.getApiConfig());
            String listUrl = (String) apiConf.get("list");
            if (StringUtils.isBlank(listUrl)) return "/" + config.getConfigKey();

            // 去掉 "get@" 前缀
            String url = listUrl.contains("@") ? listUrl.split("@")[1] : listUrl;
            // 去掉 /page 后缀
            if (url.endsWith("/page")) {
                url = url.substring(0, url.length() - 5);
            }
            // 如果是 /ai/crud/xxx 通用路由，则降级为 configKey
            if (url.startsWith("/ai/crud/") || url.startsWith("/rest/")) {
                return "/" + config.getConfigKey().replace("_", "/");
            }
            return normalizeApiBase(url);
        } catch (Exception e) {
            return "/" + config.getConfigKey().replace("_", "/");
        }
    }

    private String normalizeApiBase(String apiBase) {
        String normalized = StringUtils.defaultIfBlank(apiBase, "")
                .replace("\\", "/")
                .replaceAll("/{2,}", "/")
                .replaceAll("/+$", "");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    /**
     * 从 apiBase 中提取模块名（第一段）
     * /order/manage → order
     */
    private String resolveModuleName(String apiBase) {
        String[] parts = apiBase.split("/");
        for (String p : parts) {
            if (StringUtils.isNotBlank(p)) return p;
        }
        return "app";
    }

    /**
     * 从 apiBase 中提取业务路径（第二段起，用于 @RequestMapping）
     * /order/manage → manage
     * /order/manage/detail → manage/detail
     */
    private String resolveBusinessPath(String apiBase) {
        String[] parts = apiBase.split("/");
        List<String> nonEmpty = new ArrayList<>();
        for (String p : parts) {
            if (StringUtils.isNotBlank(p)) nonEmpty.add(p);
        }
        if (nonEmpty.size() <= 1) return nonEmpty.isEmpty() ? "data" : nonEmpty.get(0);
        return String.join("/", nonEmpty.subList(1, nonEmpty.size()));
    }

    // ───────────────────────────────────────────────────────────────────────────
    // GenTable 构建
    // ───────────────────────────────────────────────────────────────────────────

    private GenTable buildGenTable(AiCrudConfig config, String moduleName, String businessPath) {
        GenTable table = new GenTable();
        table.setTableName(config.getTableName());
        table.setTableComment(StringUtils.isNotBlank(config.getTableComment()) ? config.getTableComment() : config.getConfigKey());
        table.setFunctionName(table.getTableComment());

        String className = LowcodeCodegenOptionUtils.buildClassName(
                config.getTableName(), readOption(config, "entityPrefix", ""),
                resolveStripTablePrefixes(config));
        table.setClassName(className);
        table.setBusinessName(StringUtils.uncapitalize(className));

        // 包路径：从 options 中读取，或使用默认值
        String packageName = normalizeBasePackageName(readOption(config, "packageName", DEFAULT_PACKAGE), moduleName);
        table.setPackageName(packageName);
        table.setModuleName(moduleName);
        table.setBusinessName(resolveBusinessName(businessPath));
        table.setAuthor(readOption(config, "author", DEFAULT_AUTHOR));

        return table;
    }

    /** order/manage → manage（最后一段作为业务名） */
    String resolveBusinessName(String businessPath) {
        String[] parts = businessPath.split("/");
        return parts[parts.length - 1];
    }

    private String normalizeBasePackageName(String packageName, String moduleName) {
        String normalized = StringUtils.defaultIfBlank(packageName, DEFAULT_PACKAGE)
                .replaceAll("\\.+$", "");
        String module = StringUtils.defaultString(moduleName).trim();
        if (StringUtils.isBlank(module)) {
            return normalized;
        }
        String suffix = "." + module;
        if (normalized.endsWith(suffix)) {
            return normalized.substring(0, normalized.length() - suffix.length());
        }
        return normalized;
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 字段加载与初始化
    // ───────────────────────────────────────────────────────────────────────────

    private List<GenTableColumn> loadColumns(AiCrudConfig config) {
        String tableName = config.getTableName();
        if (StringUtils.isBlank(tableName)) return new ArrayList<>();
        try {
            List<GenTableColumn> columns = genTableColumnMapper.selectDbTableColumnsByName(tableName);
            if (columns == null || columns.isEmpty()) return loadColumnsFromModelSchema(config);
            columns.forEach(GenUtils::initColumnField);
            return columns;
        } catch (Exception e) {
            log.warn("[VelocityCodegenStrategy] 加载字段失败, tableName={}", tableName, e);
            return loadColumnsFromModelSchema(config);
        }
    }

    private List<GenTableColumn> loadColumnsFromModelSchema(AiCrudConfig config) {
        if (StringUtils.isBlank(config.getModelSchema())) {
            return new ArrayList<>();
        }
        try {
            LowcodeModelSchema modelSchema = objectMapper.readValue(config.getModelSchema(), LowcodeModelSchema.class);
            List<GenTableColumn> columns = new ArrayList<>();
            int sort = 0;
            for (LowcodeFieldSchema field : modelSchema.getFields()) {
                if (field == null || StringUtils.isBlank(field.getColumnName())) {
                    continue;
                }
                GenTableColumn column = new GenTableColumn();
                column.setColumnName(field.getColumnName());
                column.setColumnComment(StringUtils.defaultIfBlank(field.getLabel(), field.getField()));
                column.setColumnType(toColumnType(field));
                column.setJavaType(toJavaType(field.getDataType()));
                column.setJavaField(StringUtils.defaultIfBlank(field.getField(), field.getColumnName()));
                column.setIsPk(Boolean.TRUE.equals(field.getPrimaryKey()) ? 1 : 0);
                column.setIsIncrement(Boolean.TRUE.equals(field.getAutoIncrement()) ? 1 : 0);
                column.setIsRequired(Boolean.TRUE.equals(field.getRequired()) ? 1 : 0);
                column.setIsInsert(Boolean.TRUE.equals(field.getFormVisible()) && !Boolean.TRUE.equals(field.getReadonly()) ? 1 : 0);
                column.setIsEdit(Boolean.TRUE.equals(field.getFormVisible()) && !Boolean.TRUE.equals(field.getReadonly()) ? 1 : 0);
                column.setIsList(field.getListVisible() == null || Boolean.TRUE.equals(field.getListVisible()) ? 1 : 0);
                column.setIsQuery(Boolean.TRUE.equals(field.getSearchable()) ? 1 : 0);
                column.setQueryType(StringUtils.defaultIfBlank(field.getQueryType(), "EQ").toUpperCase(Locale.ROOT));
                column.setHtmlType(toHtmlType(field.getComponentType(), field.getDataType()));
                column.setDictType(StringUtils.trimToNull(field.getDictType()));
                column.setDesensitizeType(StringUtils.trimToNull(field.getSensitiveType()));
                column.setSort(sort++);
                columns.add(column);
            }
            return columns;
        } catch (Exception e) {
            log.warn("[VelocityCodegenStrategy] 从低代码模型协议构建字段失败, configKey={}", config.getConfigKey(), e);
            return new ArrayList<>();
        }
    }

    String toColumnType(LowcodeFieldSchema field) {
        String dataType = StringUtils.defaultIfBlank(field.getDataType(), "varchar").toLowerCase(Locale.ROOT);
        Integer length = field.getLength();
        Integer precision = field.getPrecision();
        return switch (dataType) {
            case "bigint" -> "bigint";
            case "int", "integer" -> "int";
            case "tinyint" -> "tinyint";
            case "decimal" -> "decimal(" + (length == null ? 18 : length) + "," + (precision == null ? 2 : precision) + ")";
            case "datetime" -> "datetime";
            case "date" -> "date";
            case "text" -> "text";
            case "char" -> "char(" + (length == null ? 1 : length) + ")";
            default -> "varchar(" + (length == null || length <= 0 ? 128 : length) + ")";
        };
    }

    String toJavaType(String dataType) {
        String type = StringUtils.defaultIfBlank(dataType, "varchar").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "bigint" -> "Long";
            case "int", "integer", "tinyint" -> "Integer";
            case "decimal" -> "BigDecimal";
            case "datetime" -> "LocalDateTime";
            case "date" -> "LocalDateTime";
            default -> "String";
        };
    }

    String toHtmlType(String componentType, String dataType) {
        String component = StringUtils.defaultString(componentType);
        if ("textarea".equals(component)) {
            return "textarea";
        }
        if ("select".equals(component)) {
            return "select";
        }
        if ("switch".equals(component)) {
            return "radio";
        }
        if ("date".equals(component) || "datetime".equals(component)
                || "date".equals(dataType) || "datetime".equals(dataType)) {
            return "datetime";
        }
        if ("fileUpload".equals(component)) {
            return "fileUpload";
        }
        if ("imageUpload".equals(component)) {
            return "imageUpload";
        }
        return "input";
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 注解控制变量解析（T3）
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * 解析 encryptConfig / dictConfig / desensitizeConfig / transConfig，
     * 生成 Velocity 上下文中的注解控制布尔值和字段集合。
     */
    private Map<String, Object> resolveAnnotationFlags(AiCrudConfig config,
                                                       List<GenTableColumn> columns) {
        return annotationContextBuilder().build(config, columns);
    }

    String normalizeDesensitizeType(String value) {
        return annotationContextBuilder().normalizeDesensitizeType(value);
    }

    private VelocityAnnotationContextBuilder annotationContextBuilder() {
        return new VelocityAnnotationContextBuilder(objectMapper);
    }

    private void renderTo(Map<String, String> files, String templatePath,
                          VelocityContext ctx, String outputPath) {
        try {
            String content = VelocityUtils.renderTemplate(templatePath, ctx);
            assertNoUnresolvedVelocityReference(content, outputPath);
            String existing = files.putIfAbsent(outputPath, content);
            if (existing != null && !StringUtils.equals(existing, content)) {
                throw new BusinessException("代码生成文件路径冲突：" + outputPath
                        + "，请检查实体前缀、表前缀和对象表名设置");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[VelocityCodegenStrategy] 渲染模板失败: {}, outputPath={}", templatePath, outputPath, e);
            throw new BusinessException("代码模板渲染失败：" + outputPath);
        }
    }

    private void assertNoUnresolvedVelocityReference(String content, String outputPath) {
        if (!StringUtils.endsWithIgnoreCase(outputPath, ".java")) {
            return;
        }
        Matcher matcher = UNRESOLVED_VELOCITY_REFERENCE.matcher(StringUtils.defaultString(content));
        if (matcher.find()) {
            throw new BusinessException("代码模板渲染失败：" + outputPath
                    + " 存在未解析模板变量 " + matcher.group());
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // 工具方法
    // ───────────────────────────────────────────────────────────────────────────

    /** order_manage → OrderManage */
    private String toPascalCase(String key) {
        if (StringUtils.isBlank(key)) return key;
        String[] parts = key.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /** 从 config.options JSON 中读取指定 key，不存在时返回 defaultValue */
    @SuppressWarnings("unchecked")
    String readOption(AiCrudConfig config, String key, String defaultValue) {
        if (StringUtils.isBlank(config.getOptions())) return defaultValue;
        try {
            Map<String, Object> opts = objectMapper.readValue(config.getOptions(), new TypeReference<>() {});
            Object val = opts.get(key);
            if (val == null && opts.get("codegen") instanceof Map<?, ?> codegen) {
                val = codegen.get(key);
                if (val == null && "packageName".equals(key)) {
                    val = codegen.get("domainPackage");
                }
            }
            return val != null ? String.valueOf(val) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean readBooleanOption(AiCrudConfig config, String key, boolean defaultValue) {
        String value = readOption(config, key, null);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    List<String> resolveStripTablePrefixes(AiCrudConfig config) {
        Object value = readRawOption(config, "stripTablePrefixes");
        return LowcodeCodegenOptionUtils.resolveStripTablePrefixes(
                null, value, LowcodeCodegenOptionUtils.DEFAULT_STRIP_TABLE_PREFIXES);
    }

    private Object readRawOption(AiCrudConfig config, String key) {
        if (StringUtils.isBlank(config.getOptions())) {
            return null;
        }
        try {
            Map<String, Object> options = objectMapper.readValue(config.getOptions(), new TypeReference<>() {});
            Object value = options.get(key);
            if (value == null && options.get("codegen") instanceof Map<?, ?> codegen) {
                value = codegen.get(key);
            }
            return value;
        } catch (Exception e) {
            throw new BusinessException("代码生成选项不是合法 JSON 对象", e);
        }
    }

    private LowcodePageSchema parsePageSchema(AiCrudConfig config) {
        if (StringUtils.isBlank(config.getPageSchema())) {
            return null;
        }
        try {
            return objectMapper.readValue(config.getPageSchema(), LowcodePageSchema.class);
        } catch (Exception e) {
            log.warn("[VelocityCodegenStrategy] 解析低代码页面协议失败, configKey={}", config.getConfigKey(), e);
            return null;
        }
    }

    private List<RelatedTableMeta> buildRelatedTables(AiCrudConfig config,
                                                      LowcodePageSchema pageSchema,
                                                      GenTable mainTable,
                                                      String moduleName,
                                                      String businessPath) {
        return relatedTablePlanner().buildRelatedTables(
                config, pageSchema, mainTable, moduleName, businessPath);
    }

    private List<RelatedTableMeta> buildMasterDetailChildren(
            Map<String, Object> masterDetailConfig,
            List<RelatedTableMeta> relatedTables,
            GenTable mainTable,
            LowcodePageSchema pageSchema) {
        return relatedTablePlanner().buildMasterDetailChildren(
                masterDetailConfig, relatedTables, mainTable, pageSchema);
    }

    private TreeCodegenMeta buildTreeMeta(GenTable mainTable,
                                          List<RelatedTableMeta> relatedTables,
                                          Map<String, Object> treeConfig) {
        return relatedTablePlanner().buildTreeMeta(mainTable, relatedTables, treeConfig);
    }

    private List<RelatedTableMeta> resolveInjectedRelatedTables(
            TreeCodegenMeta treeMeta,
            List<RelatedTableMeta> relatedTables,
            List<RelatedTableMeta> masterDetailChildren) {
        return relatedTablePlanner().resolveInjectedRelatedTables(
                treeMeta, relatedTables, masterDetailChildren);
    }

    private VelocityRelatedTablePlanner relatedTablePlanner() {
        return new VelocityRelatedTablePlanner(this);
    }

    private void renderRelatedTableFiles(Map<String, String> files,
                                         List<RelatedTableMeta> relatedTables,
                                         String javaRoot,
                                         String mapperXmlRoot,
                                         TreeCodegenMeta treeMeta) {
        if (relatedTables == null || relatedTables.isEmpty()) {
            return;
        }
        for (RelatedTableMeta meta : relatedTables) {
            VelocityContext childCtx = prepareRelatedTableContext(meta, treeMeta);
            renderTo(files, "templates/vm/entity.java.vm", childCtx, javaRoot + "entity/" + meta.getClassName() + ".java");
            renderTo(files, "templates/vm/mapper.java.vm", childCtx, javaRoot + "mapper/" + meta.getClassName() + "Mapper.java");
            renderTo(files, "templates/vm/mapper.xml.vm", childCtx,
                    mapperXmlRoot + meta.getClassName() + "Mapper.xml");
            renderTo(files, "templates/vm/dto.java.vm", childCtx, javaRoot + "dto/" + meta.getClassName() + "DTO.java");
            renderTo(files, "templates/vm/query.java.vm", childCtx, javaRoot + "dto/" + meta.getClassName() + "Query.java");
            renderTo(files, "templates/vm/service.java.vm", childCtx,
                    javaRoot + "service/I" + meta.getClassName() + "Service.java");
            renderTo(files, "templates/vm/serviceImpl.java.vm", childCtx,
                    javaRoot + "service/impl/" + meta.getClassName() + "ServiceImpl.java");
        }
    }

    private void renderRelatedTableSql(Map<String, String> files,
                                       List<RelatedTableMeta> relatedTables,
                                       TreeCodegenMeta treeMeta) {
        if (relatedTables == null || relatedTables.isEmpty()) {
            return;
        }
        for (RelatedTableMeta meta : relatedTables) {
            VelocityContext childCtx = prepareRelatedTableContext(meta, treeMeta);
            renderTo(files, "templates/vm/sql/table.sql.vm", childCtx,
                    "sql/schema/" + meta.getTableName() + ".sql");
        }
    }

    private VelocityContext prepareRelatedTableContext(RelatedTableMeta meta, TreeCodegenMeta treeMeta) {
        meta.getColumns().forEach(column -> column.setDesensitizeType(
                normalizeDesensitizeType(column.getDesensitizeType())));
        VelocityContext childCtx = VelocityUtils.prepareContext(meta.getTable());
        childCtx.put("hasDictConfig", GenUtils.hasDictTrans(meta.getColumns()));
        childCtx.put("hasDictTrans", GenUtils.hasDictTrans(meta.getColumns()));
        childCtx.put("hasDesensitize", meta.getColumns().stream()
                .anyMatch(column -> StringUtils.isNotBlank(column.getDesensitizeType())));
        childCtx.put("hasEncrypt", false);
        childCtx.put("enableDecrypt", false);
        childCtx.put("enableEncrypt", false);
        childCtx.put("hasEntityTreeFields", meta.isTreeSource());
        childCtx.put("entityTreeChildrenField", StringUtils.defaultIfBlank(meta.getTreeChildrenField(), "children"));
        childCtx.put("tree", meta.isTreeSource() ? treeMeta : null);
        childCtx.put("hasTreeConfig", meta.isTreeSource() && treeMeta != null);
        childCtx.put("isPrimaryCodegenTable", true);
        childCtx.put("isMasterDetailLayout", false);
        childCtx.put("masterDetailChildren", List.of());
        childCtx.put("injectedRelatedTables", List.of());
        childCtx.put("hasLogicDeleteColumn", meta.isHasLogicDelete());
        childCtx.put("enableServiceExtensions", false);
        return childCtx;
    }

    private String buildOwnershipManifest(Collection<String> paths,
                                          String createOnceSamplePath,
                                          String manifestPath,
                                          String userOwnedPattern) throws Exception {
        List<Map<String, Object>> files = new ArrayList<>();
        Set<String> sortedPaths = new TreeSet<>(paths);
        sortedPaths.add(manifestPath);
        for (String path : sortedPaths) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", path);
            item.put("ownership", StringUtils.equals(path, createOnceSamplePath)
                    ? "CREATE_ONCE_SAMPLE" : "GENERATED");
            files.add(item);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("ownershipVersion", "forge-codegen-file-ownership-v1");
        manifest.put("generatedPolicy", "REPLACE_WITH_REVIEW");
        manifest.put("createOncePolicy", "COPY_AND_RENAME_ONCE");
        manifest.put("userOwnedPatterns", StringUtils.isBlank(userOwnedPattern)
                ? List.of() : List.of(userOwnedPattern));
        manifest.put("userOwnedPolicy", "NEVER_GENERATED_NEVER_OVERWRITTEN");
        manifest.put("files", files);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
    }

    private void contributeStaticCodegenFiles(Map<String, String> target,
                                              AiCrudConfig config,
                                              VelocityContext context,
                                              List<LowcodeStaticCodegenContributor> contributors) {
        for (LowcodeStaticCodegenContributor contributor : contributors) {
            Map<String, String> contributed = new LinkedHashMap<>();
            contributor.contributeFiles(config, context, contributed);
            for (Map.Entry<String, String> entry : contributed.entrySet()) {
                String existing = target.putIfAbsent(entry.getKey(), entry.getValue());
                if (existing != null) {
                    throw new BusinessException("静态代码编译贡献器不能覆盖已有文件: "
                            + contributor.capabilityId() + " -> " + entry.getKey());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (StringUtils.isBlank(json)) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 预处理 columnsSchema：
     * 1. 过滤掉 actions 操作列
     * 2. 将嵌套的 render.dictType 提取到层 _dictType 字段
     * 3. 如果该字段在 transConfig 中有配置，注入 _transName（如 statusName）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> preprocessColumnsSchema(List<Map<String, Object>> columns, Map<String, Object> transConfig) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            Object key = col.get("key");
            Map<String, Object> newCol = new java.util.LinkedHashMap<>(col);
            // 提取 render.dictType
            Object renderObj = col.get("render");
            if (renderObj instanceof Map) {
                Map<String, Object> render = (Map<String, Object>) renderObj;
                if ("dictTag".equals(render.get("type")) && render.get("dictType") != null) {
                    newCol.put("_dictType", render.get("dictType"));
                }
            }
            // 提取 transConfig 中的 targetField 为 _transName
            // transConfig 的 key 是数据字段名，优先用 dataIndex，其次用 key
            Object dataIndex = col.get("dataIndex");
            String fieldName = dataIndex != null && !String.valueOf(dataIndex).isEmpty()
                    ? String.valueOf(dataIndex)
                    : (key != null ? String.valueOf(key) : "");
            if (!fieldName.isEmpty() && transConfig.containsKey(fieldName)) {
                Object transConf = transConfig.get(fieldName);
                String targetField = fieldName + "Name"; // 默认是数据字段名 + Name
                if (transConf instanceof Map) {
                    Object tf = ((Map<String, Object>) transConf).get("targetField");
                    if (tf != null && !String.valueOf(tf).isEmpty()) {
                        targetField = String.valueOf(tf);
                    }
                }
                newCol.put("_transName", targetField);
            }
            result.add(newCol);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        if (StringUtils.isBlank(json)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readNestedMap(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) map);
    }

    private String toJsonLiteral(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    boolean booleanValueDefault(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return booleanValue(value);
    }

    String resolveColumnName(List<GenTableColumn> columns, String javaField) {
        if (StringUtils.isBlank(javaField) || columns == null) {
            return camelToSnake(javaField);
        }
        return columns.stream()
                .filter(column -> StringUtils.equals(javaField, column.getJavaField())
                        || StringUtils.equals(javaField, column.getColumnName()))
                .map(GenTableColumn::getColumnName)
                .findFirst()
                .orElse(camelToSnake(javaField));
    }

    boolean hasColumn(List<GenTableColumn> columns, String columnName) {
        return columns != null && columns.stream()
                .anyMatch(column -> StringUtils.equalsIgnoreCase(columnName, column.getColumnName()));
    }

    String capJavaField(String javaField) {
        String field = normalizeJavaField(javaField);
        if (StringUtils.isBlank(field)) {
            return field;
        }
        return Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    String normalizeJavaField(String field) {
        if (StringUtils.isBlank(field)) {
            return field;
        }
        String trimmed = field.trim();
        if (trimmed.contains("_") || trimmed.contains("-")) {
            return lowerCamel(trimmed);
        }
        return trimmed;
    }

    private String lowerCamel(String value) {
        String pascal = toPascalCase(value);
        if (StringUtils.isBlank(pascal)) {
            return pascal;
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    String camelToSnake(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String normalized = value.replace("-", "_");
        return normalized
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }

    @Data
    public static class RelatedTableMeta {
        private String modelCode;
        private String key;
        private String modelName;
        private String tableName;
        private String className;
        private String variableName;
        private String mapperVarName;
        private GenTable table;
        private List<GenTableColumn> columns = new ArrayList<>();
        private GenTableColumn pkColumn;
        private boolean treeSource;
        private String treeChildrenField;
        private boolean masterDetailChild;
        private String childKey;
        private String childFkField;
        private String childFkFieldCap;
        private String childFkColumn;
        private String mainField;
        private String mainFieldCap;
        private String mainColumn;
        private boolean hasLogicDelete;
        private boolean uniqueDeleteMarker;
    }

    @Data
    public static class TreeCodegenMeta {
        private boolean separateSource;
        private String sourceModelCode;
        private String sourceTableName;
        private String className;
        private String mapperVarName;
        private String keyField;
        private String keyFieldCap;
        private String keyColumn;
        private String parentField;
        private String parentFieldCap;
        private String parentColumn;
        private String labelField;
        private String labelFieldCap;
        private String filterField;
        private String filterFieldCap;
        private String targetField;
        private String targetFieldCap;
        private String targetColumn;
        private String childrenField;
        private String childrenFieldCap;
        private String loadMode;
    }
}
