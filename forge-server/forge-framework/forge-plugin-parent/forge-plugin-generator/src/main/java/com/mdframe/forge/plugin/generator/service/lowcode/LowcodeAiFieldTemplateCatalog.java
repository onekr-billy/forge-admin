package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodeFieldSchema;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePageZone;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则 Agent 的业务对象字段模板目录与字段工厂。
 */
final class LowcodeAiFieldTemplateCatalog {

    List<LowcodeFieldSchema> fieldsForObject(LowcodeAiObjectPlan objectPlan) {
        return switch (objectPlan.code()) {
            case "customer" -> List.of(
                field("customerName", "customer_name", "客户名称", "varchar", 128, true, true, true, true, "input"),
                field("contactPerson", "contact_person", "联系人", "varchar", 64, false, true, true, true, "input"),
                field("phone", "phone", "联系电话", "varchar", 32, false, true, true, true, "input", "PHONE"),
                field("customerLevel", "customer_level", "客户等级", "varchar", 32, false, true, true, true, "select"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "contact" -> List.of(
                field("contactName", "contact_name", "联系人姓名", "varchar", 64, true, true, true, true, "input", "NAME"),
                field("customerName", "customer_name", "所属客户", "varchar", 128, false, true, true, true, "input"),
                field("phone", "phone", "手机号码", "varchar", 32, false, true, true, true, "input", "PHONE"),
                field("email", "email", "邮箱", "varchar", 128, false, true, true, true, "input", "EMAIL"),
                field("positionName", "position_name", "职务", "varchar", 64, false, false, true, true, "input"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "opportunity" -> List.of(
                field("opportunityName", "opportunity_name", "商机名称", "varchar", 128, true, true, true, true, "input"),
                field("customerName", "customer_name", "客户名称", "varchar", 128, false, true, true, true, "input"),
                field("estimatedAmount", "estimated_amount", "预计金额", "decimal", 18, false, false, true, true, "number"),
                field("stage", "stage", "阶段", "varchar", 32, false, true, true, true, "select"),
                field("ownerName", "owner_name", "负责人", "varchar", 64, false, true, true, true, "userSelect"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "contract" -> List.of(
                field("contractNo", "contract_no", "合同编号", "varchar", 64, true, true, true, true, "input"),
                field("contractName", "contract_name", "合同名称", "varchar", 128, true, true, true, true, "input"),
                field("customerName", "customer_name", "客户名称", "varchar", 128, false, true, true, true, "input"),
                field("contractAmount", "contract_amount", "合同金额", "decimal", 18, false, false, true, true, "number"),
                field("signDate", "sign_date", "签订日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "payment" -> List.of(
                field("paymentNo", "payment_no", "回款编号", "varchar", 64, true, true, true, true, "input"),
                field("customerName", "customer_name", "客户名称", "varchar", 128, false, true, true, true, "input"),
                field("contractName", "contract_name", "关联合同", "varchar", 128, false, true, true, true, "input"),
                field("paymentAmount", "payment_amount", "回款金额", "decimal", 18, true, false, true, true, "number"),
                field("paymentDate", "payment_date", "回款日期", "date", null, true, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "product" -> List.of(
                field("productCode", "product_code", "商品编码", "varchar", 64, true, true, true, true, "input"),
                field("productName", "product_name", "商品名称", "varchar", 128, true, true, true, true, "input"),
                field("categoryName", "category_name", "商品分类", "varchar", 64, false, true, true, true, "input"),
                field("unitName", "unit_name", "单位", "varchar", 32, false, false, true, true, "input"),
                field("salePrice", "sale_price", "销售价", "decimal", 18, false, false, true, true, "number"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "supplier" -> List.of(
                field("supplierName", "supplier_name", "供应商名称", "varchar", 128, true, true, true, true, "input"),
                field("contactPerson", "contact_person", "联系人", "varchar", 64, false, true, true, true, "input"),
                field("phone", "phone", "联系电话", "varchar", 32, false, true, true, true, "input", "PHONE"),
                field("address", "address", "地址", "varchar", 255, false, false, true, true, "input", "ADDRESS"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "purchase_order", "sales_order" -> List.of(
                field("orderNo", "order_no", "订单编号", "varchar", 64, true, true, true, true, "input"),
                field("partnerName", "partner_name", "往来单位", "varchar", 128, false, true, true, true, "input"),
                field("totalAmount", "total_amount", "订单金额", "decimal", 18, false, false, true, true, "number"),
                field("orderDate", "order_date", "订单日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "inventory" -> List.of(
                field("productName", "product_name", "商品名称", "varchar", 128, true, true, true, true, "input"),
                field("warehouseName", "warehouse_name", "仓库", "varchar", 64, true, true, true, true, "input"),
                field("quantity", "quantity", "库存数量", "decimal", 18, false, false, true, true, "number"),
                field("warningQuantity", "warning_quantity", "预警数量", "decimal", 18, false, false, true, true, "number"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "invoice" -> List.of(
                field("invoiceNo", "invoice_no", "发票号码", "varchar", 64, true, true, true, true, "input"),
                field("customerName", "customer_name", "客户名称", "varchar", 128, false, true, true, true, "input"),
                field("invoiceAmount", "invoice_amount", "发票金额", "decimal", 18, false, false, true, true, "number"),
                field("invoiceDate", "invoice_date", "开票日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "expense" -> List.of(
                field("expenseNo", "expense_no", "费用编号", "varchar", 64, true, true, true, true, "input"),
                field("expenseType", "expense_type", "费用类型", "varchar", 32, false, true, true, true, "select"),
                field("amount", "amount", "金额", "decimal", 18, true, false, true, true, "number"),
                field("expenseDate", "expense_date", "发生日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "employee" -> List.of(
                field("employeeNo", "employee_no", "员工编号", "varchar", 64, true, true, true, true, "input"),
                field("employeeName", "employee_name", "员工姓名", "varchar", 64, true, true, true, true, "input", "NAME"),
                field("departmentName", "department_name", "部门", "varchar", 64, false, true, true, true, "orgTreeSelect"),
                field("phone", "phone", "手机号", "varchar", 32, false, true, true, true, "input", "PHONE"),
                field("hireDate", "hire_date", "入职日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "department", "category" -> List.of(
                field("name", "name", objectPlan.name() + "名称", "varchar", 128, true, true, true, true, "input"),
                field("code", "code", objectPlan.name() + "编码", "varchar", 64, true, true, true, true, "input"),
                field("sortNo", "sort_no", "排序", "int", null, false, false, true, true, "number"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea"));
            case "leave_request" -> List.of(
                field("employeeName", "employee_name", "申请人", "varchar", 64, true, true, true, true, "userSelect", "NAME"),
                field("leaveType", "leave_type", "请假类型", "varchar", 32, true, true, true, true, "select"),
                field("startTime", "start_time", "开始时间", "datetime", null, true, true, true, true, "datetime"),
                field("endTime", "end_time", "结束时间", "datetime", null, true, true, true, true, "datetime"),
                field("reason", "reason", "请假原因", "text", null, false, false, true, true, "textarea"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "project" -> List.of(
                field("projectCode", "project_code", "项目编码", "varchar", 64, true, true, true, true, "input"),
                field("projectName", "project_name", "项目名称", "varchar", 128, true, true, true, true, "input"),
                field("ownerName", "owner_name", "负责人", "varchar", 64, false, true, true, true, "userSelect"),
                field("startDate", "start_date", "开始日期", "date", null, false, true, true, true, "date"),
                field("endDate", "end_date", "结束日期", "date", null, false, true, true, true, "date"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "task" -> List.of(
                field("taskName", "task_name", "任务名称", "varchar", 128, true, true, true, true, "input"),
                field("projectName", "project_name", "所属项目", "varchar", 128, false, true, true, true, "input"),
                field("assigneeName", "assignee_name", "负责人", "varchar", 64, false, true, true, true, "userSelect"),
                field("dueDate", "due_date", "截止日期", "date", null, false, true, true, true, "date"),
                field("priority", "priority", "优先级", "varchar", 32, false, true, true, true, "select"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"));
            case "work_order" -> List.of(
                field("workOrderNo", "work_order_no", "工单编号", "varchar", 64, true, true, true, true, "input"),
                field("title", "title", "工单标题", "varchar", 128, true, true, true, true, "input"),
                field("handlerName", "handler_name", "处理人", "varchar", 64, false, true, true, true, "userSelect"),
                field("priority", "priority", "优先级", "varchar", 32, false, true, true, true, "select"),
                field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
                field("description", "description", "问题描述", "text", null, false, false, true, true, "textarea"));
            default -> defaultFields(objectPlan.name());
        };
    }

    List<LowcodeFieldSchema> defaultFields(String objectName) {
        return List.of(
            field("name", "name", objectName + "名称", "varchar", 128, true, true, true, true, "input"),
            field("code", "code", objectName + "编码", "varchar", 64, false, true, true, true, "input"),
            field("status", "status", "状态", "varchar", 32, false, true, true, true, "select"),
            field("remark", "remark", "备注", "text", null, false, false, true, true, "textarea")
        );
    }

    LowcodePageZone zone(String key, String component, List<String> fields) {
        LowcodePageZone zone = new LowcodePageZone();
        zone.setZoneKey(key);
        zone.setComponentKey(component);
        zone.setEnabled(true);
        zone.setFieldRefs(fields == null ? new ArrayList<>() : new ArrayList<>(fields));
        return zone;
    }

    LowcodeFieldSchema field(String field, String column, String label, String dataType,
                             Integer length, boolean required, boolean searchable,
                             boolean listVisible, boolean formVisible, String component) {
        return field(field, column, label, dataType, length, required, searchable,
            listVisible, formVisible, component, "NONE");
    }

    private LowcodeFieldSchema field(String field, String column, String label, String dataType,
                                     Integer length, boolean required, boolean searchable,
                                     boolean listVisible, boolean formVisible, String component,
                                     String sensitiveType) {
        LowcodeFieldSchema schema = new LowcodeFieldSchema();
        schema.setField(field);
        schema.setColumnName(column);
        schema.setLabel(label);
        schema.setDataType(dataType);
        schema.setLength(length);
        schema.setPrecision("decimal".equals(dataType) ? 2 : null);
        schema.setRequired(required);
        schema.setSearchable(searchable);
        schema.setListVisible(listVisible);
        schema.setFormVisible(formVisible);
        schema.setComponentType(component);
        schema.setQueryType("varchar".equals(dataType) && searchable ? "like" : "eq");
        schema.setSensitiveType(StringUtils.defaultIfBlank(sensitiveType, "NONE"));
        schema.setSystemField(false);
        schema.setReadonly(false);
        schema.setSortable("date".equals(dataType) || "datetime".equals(dataType)
            || "decimal".equals(dataType) || "int".equals(dataType));
        schema.setWidth("text".equals(dataType) ? 220 : 160);
        return schema;
    }
}
