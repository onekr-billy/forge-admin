package com.mdframe.forge.plugin.generator.service.lowcode;

import java.util.Set;

/**
 * 低代码设计器组件合同。设计器、模型校验和运行时编译必须共享这份清单，
 * 避免组件可以拖入画布却在保存或发布时被当成未知类型丢弃。
 */
public final class LowcodeComponentCatalog {

    private LowcodeComponentCatalog() {
    }

    public static final Set<String> FIELD_COMPONENT_KEYS = Set.of(
            "input", "barcodeScanner", "textarea", "number", "inputNumber", "input-number", "inputnumber",
            "integer", "money", "slider", "rate", "color", "date", "datetime", "daterange", "datetimerange",
            "month", "year", "time", "timerange", "switch", "select", "radio", "radioButton", "checkbox",
            "transfer", "dictSelect", "cascader", "treeSelect", "customSelect", "regionTreeSelect",
            "orgTreeSelect", "orgSelect", "departmentSelect", "departmentTreeSelect", "deptSelect",
            "deptTreeSelect", "elTreeSelect", "orgName", "deptName", "userSelect", "userPicker", "userName",
            "fileUpload", "imageUpload", "upload", "objectReference", "recordSelector", "text"
    );

    public static final Set<String> PAGE_WIDGET_COMPONENT_KEYS = Set.of(
            "rich-text", "watermark", "vue-component", "html-tag", "markdown", "barcode", "qrcode", "calendar",
            "code", "countdown", "descriptions", "announcement", "list", "log", "number-animation", "breadcrumb",
            "menu", "pagination", "split"
    );

    public static final Set<String> STRUCTURED_VALUE_COMPONENT_KEYS = Set.of(
            "checkbox", "transfer", "daterange", "datetimerange", "timerange"
    );

    public static boolean isFieldComponent(String componentKey) {
        return componentKey != null && FIELD_COMPONENT_KEYS.contains(componentKey);
    }

    public static boolean isPageWidgetComponent(String componentKey) {
        return componentKey != null && PAGE_WIDGET_COMPONENT_KEYS.contains(componentKey);
    }

    public static boolean isStructuredValueComponent(String componentKey) {
        return componentKey != null && STRUCTURED_VALUE_COMPONENT_KEYS.contains(componentKey);
    }
}
