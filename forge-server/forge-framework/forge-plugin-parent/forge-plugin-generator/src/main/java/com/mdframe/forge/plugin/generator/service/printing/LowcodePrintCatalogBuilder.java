package com.mdframe.forge.plugin.generator.service.printing;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.print.service.PrintDocumentAccess;
import com.mdframe.forge.plugin.print.service.PrintFailure;
import com.mdframe.forge.plugin.print.vo.PrintFieldCatalogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class LowcodePrintCatalogBuilder {
    private final PrintDocumentAccess access;

    public PrintFieldCatalogVO build(PrintMetadataResolver.Metadata metadata) {
        List<PrintFieldCatalogVO.Field> fields = new ArrayList<>();
        append(fields, "main", metadata.main(), null);
        for (var child : metadata.children()) {
            String path = "children." + child.key();
            fields.add(new PrintFieldCatalogVO.Field(path, child.key(), "COLLECTION"));
            append(fields, path, child.model(), child.fields());
        }
        var result = new PrintFieldCatalogVO(fields);
        access.catalog(result);
        return result;
    }

    private void append(List<PrintFieldCatalogVO.Field> output, String prefix,
                        PrintMetadataResolver.Model model, List<Map<String, Object>> allowed) {
        Set<String> pageFields = new HashSet<>();
        boolean constrained = model.page().getZones().stream().anyMatch(zone ->
                Set.of("table", "edit", "detail").contains(Objects.toString(zone.getZoneKey(), "")));
        if (allowed == null && constrained) {
            model.page().getZones().stream().filter(zone -> !Boolean.FALSE.equals(zone.getEnabled())
                    && Set.of("table", "edit", "detail").contains(Objects.toString(zone.getZoneKey(), "")))
                    .forEach(zone -> pageFields.addAll(zone.getFieldRefs()));
        }
        for (var field : model.schema().getFields()) {
            boolean platformManaged = isPlatformManagedPrintField(field);
            // 页面 zone 只约束业务表单字段；flowStatus 等平台托管字段允许打印模板直接引用
            if (!platformManaged && allowed == null && constrained && !pageFields.contains(field.getField())) {
                continue;
            }
            if (!visible(field)) {
                continue;
            }
            if (allowed != null && allowed.stream().noneMatch(ref ->
                    Objects.equals(field.getField(), ref.getOrDefault("sourceField", ref.get("field")))
                            && !"HIDDEN".equals(ref.get("fieldStatus")) && !"DISABLED".equals(ref.get("fieldStatus"))
                            && (!Boolean.FALSE.equals(ref.get("formVisible")) || !Boolean.FALSE.equals(ref.get("listVisible"))))) {
                continue;
            }
            if (field.getField() == null || !field.getField().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw PrintFailure.field(prefix, "打印字段标识不合法");
            }
            output.add(new PrintFieldCatalogVO.Field(prefix + "." + field.getField(),
                    field.getLabel() == null ? field.getField() : field.getLabel(), type(field)));
        }
    }

    /** 流程状态等平台字段不依赖页面 fieldRefs，避免发布时被当成“未授权字段”。 */
    boolean isPlatformManagedPrintField(LowcodeFieldSchema field) {
        if (field == null || field.getField() == null) {
            return false;
        }
        if ("flowStatus".equals(field.getField()) || "flow_status".equals(field.getColumnName())) {
            return true;
        }
        Map<String, Object> props = field.getAdvancedProps();
        return props != null && "BUSINESS_FLOW".equals(String.valueOf(props.get("managedBy")));
    }

    public boolean visible(LowcodeFieldSchema field) {
        return field != null && !Set.of("HIDDEN", "DISABLED").contains(Objects.toString(field.getFieldStatus(), ""))
                && (!Boolean.FALSE.equals(field.getFormVisible()) || !Boolean.FALSE.equals(field.getListVisible()))
                && !"PASSWORD".equals(field.getSensitiveType());
    }

    public String type(LowcodeFieldSchema field) {
        if ((field.getSensitiveType() != null && !"NONE".equals(field.getSensitiveType()))
                || field.isSelectionLabelField() || (field.getDictType() != null && !field.getDictType().isBlank())) {
            return "TEXT";
        }
        if ("imageUpload".equals(field.getComponentType())) {
            return "IMAGE";
        }
        if ("money".equals(field.getComponentType())) {
            return "MONEY";
        }
        String business = Objects.toString(field.getBusinessFieldType(), "");
        if (Set.of("MONEY", "DATE", "BOOLEAN", "NUMBER").contains(business)) {
            return business;
        }
        String dataType = Objects.toString(field.getDataType(), "").toLowerCase(Locale.ROOT);
        if (Set.of("bigint", "int", "integer", "smallint", "decimal", "double", "float", "number").contains(dataType)) {
            return "NUMBER";
        }
        if (Set.of("date", "datetime", "timestamp").contains(dataType)) {
            return "DATE";
        }
        return "TEXT";
    }
}
