package com.mdframe.forge.business.core.purchase.support;

import com.mdframe.forge.business.core.purchase.enums.SamplePurchaseOrderStatus;
import com.mdframe.forge.business.core.purchase.dto.SamplePurchaseOrderTaskSaveDTO;
import com.mdframe.forge.business.core.purchase.vo.SamplePurchaseOrderVO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormContextQueryDTO;
import com.mdframe.forge.plugin.generator.dto.businessapp.BusinessTaskFormSaveDTO;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessTaskFormContextVO;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购单审批示例的流程/表单默认定义。
 * <p>
 * 应用管理保存的 codeAppMetadata 会覆盖这些默认资产；这里保留的是代码业务接入流程时
 * 必须由业务模块提供的默认模板，便于后续生成同类业务流程示例。
 */
@UtilityClass
public class SamplePurchaseOrderFlowDefinition {

    public static final String BUSINESS_TYPE = "sample_purchase_order";
    public static final String BUSINESS_OBJECT_NAME = "采购申请";
    public static final String BUSINESS_APP_NAME = "采购申请";

    public static final String MODEL_KEY = "sample_purchase_order_approval";
    public static final String MODEL_NAME = "采购单审批测试流程";
    public static final String MODEL_DESCRIPTION = "用于验证业务模块与流程模块联动：部门负责人、工程部经理、会签、驳回修改、抄送。";
    public static final String MODEL_CATEGORY = "purchase";
    public static final String FLOW_TYPE = "purchase";
    public static final String DESIGNER_TYPE = "approval";
    public static final String FORM_TYPE_BUSINESS = "business";
    public static final String NOTIFY_TYPE_REDIS = "redis";

    public static final String PROVIDER_KEY = "samplePurchaseOrder";
    public static final String PROVIDER_NAME = "采购单审批代码表单";
    public static final String FORM_KEY = "sample_purchase_order_approval_form";
    public static final String FORM_NAME = "采购单审批表单";
    public static final String FORM_URL = "/business/purchase-order-test";
    public static final String FORM_MODE_BUSINESS_CODE = "BUSINESS_CODE_FORM";
    public static final String TASK_FORM_TYPE_BUSINESS_CODE = "business-code";

    public static final String STATUS_DRAFT = SamplePurchaseOrderStatus.DRAFT.getCode();
    public static final String STATUS_IN_PROCESS = SamplePurchaseOrderStatus.IN_PROCESS.getCode();
    public static final String STATUS_NEED_MODIFY = SamplePurchaseOrderStatus.NEED_MODIFY.getCode();
    public static final String STATUS_APPROVED = SamplePurchaseOrderStatus.APPROVED.getCode();
    public static final String STATUS_REJECTED = SamplePurchaseOrderStatus.REJECTED.getCode();
    public static final String STATUS_CANCELED = SamplePurchaseOrderStatus.CANCELED.getCode();

    public static final String NODE_DEPT_LEADER_APPROVE = "dept_leader_approve";
    public static final String NODE_ENGINEERING_MANAGER_APPROVE = "engineering_manager_approve";
    public static final String NODE_PURCHASE_COUNTERSIGN = "purchase_countersign";
    public static final String NODE_APPLICANT_MODIFY = "applicant_modify";

    public static final String VAR_BUSINESS_KEY = "businessKey";
    public static final String VAR_OBJECT_CODE = "objectCode";
    public static final String VAR_RECORD_ID = "recordId";
    public static final String VAR_PURCHASE_ORDER_ID = "purchaseOrderId";
    public static final String VAR_ORDER_NO = "orderNo";
    public static final String VAR_TITLE = "title";
    public static final String VAR_AMOUNT_CENT = "amountCent";
    public static final String VAR_INITIATOR = "initiator";
    public static final String VAR_DEPT_LEADER_ID = "deptLeaderId";
    public static final String VAR_ENGINEERING_MANAGER_ID = "engineeringManagerId";
    public static final String VAR_COUNTERSIGN_USER_LIST = "countersignUserList";
    public static final String VAR_CC_ROLE_KEYS = "ccRoleKeys";
    public static final String VAR_ASSIGNEE = "assignee";
    public static final String VAR_APPROVAL_RESULT = "approvalResult";
    public static final String VAR_APPROVED = "approved";

    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";

    public static final String FIELD_ID = "id";
    public static final String FIELD_ORDER_NO = "orderNo";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_SUPPLIER_NAME = "supplierName";
    public static final String FIELD_AMOUNT_CENT = "amountCent";
    public static final String FIELD_PURCHASE_ITEMS = "purchaseItems";
    public static final String FIELD_NEED_DATE = "needDate";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_APPLICANT_ID = "applicantId";
    public static final String FIELD_APPLICANT_NAME = "applicantName";
    public static final String FIELD_APPLICANT_DEPT_ID = "applicantDeptId";
    public static final String FIELD_APPLICANT_DEPT_NAME = "applicantDeptName";
    public static final String FIELD_BUSINESS_KEY = "businessKey";
    public static final String FIELD_PROCESS_INSTANCE_ID = "processInstanceId";
    public static final String FIELD_DEPT_LEADER_ID = "deptLeaderId";
    public static final String FIELD_DEPT_LEADER_NAME = "deptLeaderName";
    public static final String FIELD_ENGINEERING_MANAGER_ID = "engineeringManagerId";
    public static final String FIELD_ENGINEERING_MANAGER_NAME = "engineeringManagerName";
    public static final String FIELD_COUNTERSIGN_USER_IDS = "countersignUserIds";
    public static final String FIELD_COUNTERSIGN_USER_NAMES = "countersignUserNames";
    public static final String FIELD_CC_ROLE_KEYS = "ccRoleKeys";
    public static final String FIELD_ARRIVAL_LIST_FILE_IDS = "arrivalListFileIds";
    public static final String FIELD_APPLICANT_MODIFY_REMARK = "applicantModifyRemark";
    public static final String FIELD_DEPT_LEADER_REMARK = "deptLeaderRemark";
    public static final String FIELD_ENGINEERING_MANAGER_REMARK = "engineeringManagerRemark";
    public static final String FIELD_COUNTERSIGN_REMARK = "countersignRemark";
    public static final String FIELD_REJECT_REASON = "rejectReason";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_UPDATE_TIME = "updateTime";

    private static final List<FieldDefinition> FIELD_DEFINITIONS = List.of(
            field(FIELD_ID, "采购单ID", "number", true, true, true, "系统主键，默认不展示"),
            field(FIELD_ORDER_NO, "采购单号", "input", true),
            field(FIELD_TITLE, "采购主题", "input"),
            field(FIELD_SUPPLIER_NAME, "供应商", "input"),
            field(FIELD_AMOUNT_CENT, "采购金额（分）", "number"),
            field(FIELD_PURCHASE_ITEMS, "采购明细", "textarea"),
            field(FIELD_NEED_DATE, "期望到货日期", "date"),
            field(FIELD_STATUS, "流程状态", "select", true),
            field(FIELD_APPLICANT_ID, "申请人ID", "number", true, true, true, "系统用户 ID，默认不展示"),
            field(FIELD_APPLICANT_NAME, "申请人", "input", true),
            field(FIELD_APPLICANT_DEPT_ID, "申请部门ID", "number", true, true, true, "组织 ID，默认不展示"),
            field(FIELD_APPLICANT_DEPT_NAME, "申请部门", "input", true),
            field(FIELD_BUSINESS_KEY, "业务Key", "input", true, true, true, "流程业务键，默认不展示"),
            field(FIELD_PROCESS_INSTANCE_ID, "流程实例ID", "input", true, true, true, "流程实例 ID，默认不展示"),
            field(FIELD_DEPT_LEADER_ID, "部门负责人ID", "number", true, true, true, "审批人 ID，默认不展示"),
            field(FIELD_DEPT_LEADER_NAME, "部门负责人", "input", true),
            field(FIELD_ENGINEERING_MANAGER_ID, "工程部经理ID", "number", true, true, true, "审批人 ID，默认不展示"),
            field(FIELD_ENGINEERING_MANAGER_NAME, "工程部经理", "input", true),
            field(FIELD_COUNTERSIGN_USER_IDS, "会签人员ID", "input", true, true, true, "会签用户 ID，默认不展示"),
            field(FIELD_COUNTERSIGN_USER_NAMES, "会签人员", "input", true),
            field(FIELD_CC_ROLE_KEYS, "抄送角色", "input", true, true, true, "抄送角色编码，默认不展示"),
            field(FIELD_ARRIVAL_LIST_FILE_IDS, "上传清单", "fileUpload"),
            field(FIELD_APPLICANT_MODIFY_REMARK, "申请人修改说明", "textarea"),
            field(FIELD_DEPT_LEADER_REMARK, "部门负责人意见", "textarea"),
            field(FIELD_ENGINEERING_MANAGER_REMARK, "工程部经理意见", "textarea"),
            field(FIELD_COUNTERSIGN_REMARK, "会签意见", "textarea"),
            field(FIELD_REJECT_REASON, "驳回原因", "textarea", true),
            field(FIELD_REMARK, "备注", "textarea"),
            field(FIELD_CREATE_TIME, "创建时间", "datetime", true, true, true, "审计字段，默认不展示"),
            field(FIELD_UPDATE_TIME, "更新时间", "datetime", true, true, true, "审计字段，默认不展示")
    );

    private static final Map<String, FieldDefinition> FIELD_INDEX = FIELD_DEFINITIONS.stream()
            .collect(Collectors.toMap(FieldDefinition::fieldCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));

    public static boolean supportsObject(String objectCode) {
        return !StringUtils.hasText(objectCode) || BUSINESS_TYPE.equalsIgnoreCase(objectCode);
    }

    public static List<Map<String, Object>> formAssets(String objectCode) {
        if (!supportsObject(objectCode)) {
            return List.of();
        }
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("appName", BUSINESS_APP_NAME);
        asset.put("objectName", BUSINESS_OBJECT_NAME);
        asset.put("businessName", BUSINESS_OBJECT_NAME);
        asset.put("formKey", FORM_KEY);
        asset.put("formName", FORM_NAME);
        asset.put("formMode", FORM_MODE_BUSINESS_CODE);
        asset.put("providerKey", PROVIDER_KEY);
        asset.put("formUrl", FORM_URL);
        asset.put("fields", fields(null));
        asset.put("fieldCatalog", fields(null));
        asset.put("supportsSave", true);
        asset.put("description", "代码实现的采购单审批表单，支持按流程节点控制字段。");
        return List.of(asset);
    }

    public static List<Map<String, Object>> fields(Long recordId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (FieldDefinition definition : FIELD_DEFINITIONS) {
            Map<String, Object> item = definition.toMap();
            if (FIELD_ARRIVAL_LIST_FILE_IDS.equals(definition.fieldCode())) {
                item.put("businessType", BUSINESS_TYPE);
                item.put("businessId", recordId == null ? null : String.valueOf(recordId));
                item.put("limit", 5);
                item.put("fileSize", 20);
                item.put("uploadButtonText", "上传清单");
            }
            result.add(item);
        }
        return result;
    }

    public static BusinessTaskFormContextVO buildTaskFormContext(BusinessTaskFormContextQueryDTO query,
                                                                  Map<String, Object> formRef,
                                                                  List<Map<String, Object>> fieldPermissions,
                                                                  Long recordId,
                                                                  String summary,
                                                                  boolean configured) {
        BusinessTaskFormContextQueryDTO contextQuery = query == null ? new BusinessTaskFormContextQueryDTO() : query;
        BusinessTaskFormContextVO vo = new BusinessTaskFormContextVO();
        vo.setConfigured(configured);
        vo.setFormType(TASK_FORM_TYPE_BUSINESS_CODE);
        vo.setTaskId(contextQuery.getTaskId());
        vo.setBusinessKey(contextQuery.getBusinessKey());
        vo.setProcessInstanceId(contextQuery.getProcessInstanceId());
        vo.setProcessDefKey(contextQuery.getProcessDefKey());
        vo.setTaskDefKey(contextQuery.getTaskDefKey());
        vo.setObjectCode(BUSINESS_TYPE);
        vo.setBusinessObjectName(BUSINESS_OBJECT_NAME);
        vo.setBusinessSummary(summary);
        vo.setRecordId(recordId);
        vo.setFormKey(FORM_KEY);
        vo.setFormName(FORM_NAME);
        vo.setProviderKey(PROVIDER_KEY);
        vo.setFormUrl(FORM_URL);
        vo.setFormRef(formRef == null ? new LinkedHashMap<>() : new LinkedHashMap<>(formRef));
        vo.setFieldPermissions(fieldPermissions == null ? List.of() : fieldPermissions);
        vo.setFields(fields(recordId));
        return vo;
    }

    public static SamplePurchaseOrderTaskSaveDTO toTaskSaveDTO(BusinessTaskFormSaveDTO dto) {
        SamplePurchaseOrderTaskSaveDTO saveDTO = new SamplePurchaseOrderTaskSaveDTO();
        if (dto == null) {
            return saveDTO;
        }
        saveDTO.setId(dto.getRecordId());
        saveDTO.setBusinessKey(dto.getBusinessKey());
        saveDTO.setTaskId(dto.getTaskId());
        saveDTO.setTaskDefKey(dto.getTaskDefKey());
        Map<String, Object> data = dto.getData() == null ? Map.of() : dto.getData();
        saveDTO.setTitle(text(data.get(FIELD_TITLE)));
        saveDTO.setSupplierName(text(data.get(FIELD_SUPPLIER_NAME)));
        saveDTO.setAmountCent(longValue(data.get(FIELD_AMOUNT_CENT)));
        saveDTO.setPurchaseItems(text(data.get(FIELD_PURCHASE_ITEMS)));
        saveDTO.setNeedDate(localDateValue(data.get(FIELD_NEED_DATE)));
        saveDTO.setArrivalListFileIds(text(data.get(FIELD_ARRIVAL_LIST_FILE_IDS)));
        saveDTO.setApplicantModifyRemark(text(data.get(FIELD_APPLICANT_MODIFY_REMARK)));
        saveDTO.setDeptLeaderRemark(text(data.get(FIELD_DEPT_LEADER_REMARK)));
        saveDTO.setEngineeringManagerRemark(text(data.get(FIELD_ENGINEERING_MANAGER_REMARK)));
        saveDTO.setCountersignRemark(text(data.get(FIELD_COUNTERSIGN_REMARK)));
        return saveDTO;
    }

    public static Map<String, Object> recordData(SamplePurchaseOrderVO detail) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (detail == null) {
            return data;
        }
        data.put(FIELD_ID, detail.getId());
        data.put(FIELD_ORDER_NO, detail.getOrderNo());
        data.put(FIELD_TITLE, detail.getTitle());
        data.put(FIELD_SUPPLIER_NAME, detail.getSupplierName());
        data.put(FIELD_AMOUNT_CENT, detail.getAmountCent());
        data.put(FIELD_PURCHASE_ITEMS, detail.getPurchaseItems());
        data.put(FIELD_NEED_DATE, detail.getNeedDate());
        data.put(FIELD_STATUS, detail.getStatus());
        data.put(FIELD_APPLICANT_ID, detail.getApplicantId());
        data.put(FIELD_APPLICANT_NAME, detail.getApplicantName());
        data.put(FIELD_APPLICANT_DEPT_ID, detail.getApplicantDeptId());
        data.put(FIELD_APPLICANT_DEPT_NAME, detail.getApplicantDeptName());
        data.put(FIELD_BUSINESS_KEY, detail.getBusinessKey());
        data.put(FIELD_PROCESS_INSTANCE_ID, detail.getProcessInstanceId());
        data.put(FIELD_DEPT_LEADER_ID, detail.getDeptLeaderId());
        data.put(FIELD_DEPT_LEADER_NAME, detail.getDeptLeaderName());
        data.put(FIELD_ENGINEERING_MANAGER_ID, detail.getEngineeringManagerId());
        data.put(FIELD_ENGINEERING_MANAGER_NAME, detail.getEngineeringManagerName());
        data.put(FIELD_COUNTERSIGN_USER_IDS, detail.getCountersignUserIds());
        data.put(FIELD_COUNTERSIGN_USER_NAMES, detail.getCountersignUserNames());
        data.put(FIELD_CC_ROLE_KEYS, detail.getCcRoleKeys());
        data.put(FIELD_ARRIVAL_LIST_FILE_IDS, detail.getArrivalListFileIds());
        data.put(FIELD_APPLICANT_MODIFY_REMARK, detail.getApplicantModifyRemark());
        data.put(FIELD_DEPT_LEADER_REMARK, detail.getDeptLeaderRemark());
        data.put(FIELD_ENGINEERING_MANAGER_REMARK, detail.getEngineeringManagerRemark());
        data.put(FIELD_COUNTERSIGN_REMARK, detail.getCountersignRemark());
        data.put(FIELD_REJECT_REASON, detail.getRejectReason());
        data.put(FIELD_REMARK, detail.getRemark());
        data.put(FIELD_CREATE_TIME, detail.getCreateTime());
        data.put(FIELD_UPDATE_TIME, detail.getUpdateTime());
        return data;
    }

    public static String buildSummary(Map<String, Object> recordData) {
        if (recordData == null || recordData.isEmpty()) {
            return null;
        }
        return buildSummary(text(recordData.get(FIELD_ORDER_NO)), text(recordData.get(FIELD_APPLICANT_NAME)));
    }

    public static String buildSummary(SamplePurchaseOrderVO detail) {
        if (detail == null) {
            return null;
        }
        return buildSummary(detail.getOrderNo(), detail.getApplicantName());
    }

    public static String buildSummary(String orderNoValue, String applicantValue) {
        String orderNo = StringUtils.hasText(orderNoValue) ? orderNoValue : "未编号";
        String applicant = StringUtils.hasText(applicantValue) ? applicantValue : "未知申请人";
        return "采购申请单 · " + orderNo + " · " + applicant;
    }

    public static Long resolveRecordId(Long recordId, String businessKey) {
        return recordId != null ? recordId : parseBusinessRecordId(businessKey);
    }

    public static Long parseBusinessRecordId(String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            return null;
        }
        int index = businessKey.lastIndexOf(':');
        if (index < 0 || index == businessKey.length() - 1) {
            return null;
        }
        return longValue(businessKey.substring(index + 1));
    }

    public static String businessKey(Long id) {
        return id == null ? null : BUSINESS_TYPE + ":" + id;
    }

    public static Map<String, Object> flowModelPayload(Object id, Long tenantId, String bpmnXml) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("tenantId", tenantId);
        payload.put("modelKey", MODEL_KEY);
        payload.put("modelName", MODEL_NAME);
        payload.put("description", MODEL_DESCRIPTION);
        payload.put("category", MODEL_CATEGORY);
        payload.put("flowType", FLOW_TYPE);
        payload.put("designerType", DESIGNER_TYPE);
        payload.put("formType", FORM_TYPE_BUSINESS);
        payload.put("formId", FORM_KEY);
        payload.put("formJson", defaultBusinessFormRefJson());
        payload.put("notifyType", NOTIFY_TYPE_REDIS);
        payload.put("bpmnXml", bpmnXml);
        return payload;
    }

    public static String defaultBusinessFormRefJson() {
        return """
                {
                  "type": "%s",
                  "formMode": "%s",
                  "objectCode": "%s",
                  "objectName": "%s",
                  "formKey": "%s",
                  "formName": "%s",
                  "providerKey": "%s",
                  "formUrl": "%s",
                  "formRef": {
                    "type": "%s",
                    "formMode": "%s",
                    "objectCode": "%s",
                    "providerKey": "%s",
                    "formKey": "%s",
                    "formUrl": "%s"
                  }
                }
                """.formatted(
                FORM_MODE_BUSINESS_CODE,
                FORM_MODE_BUSINESS_CODE,
                BUSINESS_TYPE,
                BUSINESS_OBJECT_NAME,
                FORM_KEY,
                FORM_NAME,
                PROVIDER_KEY,
                FORM_URL,
                FORM_MODE_BUSINESS_CODE,
                FORM_MODE_BUSINESS_CODE,
                BUSINESS_TYPE,
                PROVIDER_KEY,
                FORM_KEY,
                FORM_URL);
    }

    public static boolean isApplicantModifyNode(String taskDefKey) {
        return NODE_APPLICANT_MODIFY.equals(taskDefKey);
    }

    public static boolean isApprovalNode(String taskDefKey) {
        return NODE_DEPT_LEADER_APPROVE.equals(taskDefKey)
                || NODE_ENGINEERING_MANAGER_APPROVE.equals(taskDefKey)
                || NODE_PURCHASE_COUNTERSIGN.equals(taskDefKey);
    }

    public static boolean isRejectAction(String approvalResult) {
        return ACTION_REJECT.equalsIgnoreCase(approvalResult);
    }

    public static boolean isApproveAction(String approvalResult) {
        return ACTION_APPROVE.equalsIgnoreCase(approvalResult);
    }

    public static String variableExpression(String variableName) {
        return "${" + variableName + "}";
    }

    public static String rejectConditionExpression() {
        return "${" + VAR_APPROVAL_RESULT + " == '" + ACTION_REJECT + "'}";
    }

    public static String countersignCompletionConditionExpression() {
        return "${" + VAR_APPROVED + " == false || nrOfCompletedInstances == nrOfInstances}";
    }

    public static String nodeFieldPermissionsJson(String nodeKey) {
        Collection<NodeFieldPermission> permissions = switch (nodeKey) {
            case NODE_DEPT_LEADER_APPROVE -> List.of(
                    writablePermission(FIELD_ARRIVAL_LIST_FILE_IDS, false),
                    writablePermission(FIELD_DEPT_LEADER_REMARK, false)
            );
            case NODE_ENGINEERING_MANAGER_APPROVE -> List.of(
                    writablePermission(FIELD_ENGINEERING_MANAGER_REMARK, false)
            );
            case NODE_PURCHASE_COUNTERSIGN -> List.of(
                    writablePermission(FIELD_COUNTERSIGN_REMARK, false)
            );
            case NODE_APPLICANT_MODIFY -> List.of(
                    writablePermission(FIELD_TITLE, true),
                    writablePermission(FIELD_SUPPLIER_NAME, true),
                    writablePermission(FIELD_AMOUNT_CENT, true),
                    writablePermission(FIELD_PURCHASE_ITEMS, false),
                    writablePermission(FIELD_NEED_DATE, false),
                    writablePermission(FIELD_APPLICANT_MODIFY_REMARK, false)
            );
            default -> List.of();
        };
        return permissions.stream()
                .map(NodeFieldPermission::toJson)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static FieldDefinition field(String fieldCode, String label, String componentType) {
        return field(fieldCode, label, componentType, false);
    }

    private static FieldDefinition field(String fieldCode, String label, String componentType, boolean readonly) {
        return field(fieldCode, label, componentType, readonly, false, false, null);
    }

    private static FieldDefinition field(String fieldCode, String label, String componentType,
                                         boolean readonly, boolean internal,
                                         boolean systemField, String description) {
        return new FieldDefinition(fieldCode, label, componentType, readonly, internal, systemField, description);
    }

    private static NodeFieldPermission writablePermission(String fieldCode, boolean required) {
        return new NodeFieldPermission(fieldCode, labelOf(fieldCode), true, true, required);
    }

    private static String labelOf(String fieldCode) {
        FieldDefinition definition = FIELD_INDEX.get(fieldCode);
        return definition == null ? fieldCode : definition.label();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate localDateValue(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private record FieldDefinition(String fieldCode,
                                   String label,
                                   String componentType,
                                   boolean readonly,
                                   boolean internal,
                                   boolean systemField,
                                   String description) {

        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("field", fieldCode);
            item.put("fieldCode", fieldCode);
            item.put("label", label);
            item.put("type", componentType);
            item.put("componentType", componentType);
            item.put("visible", !internal);
            item.put("writable", !readonly);
            item.put("readonly", readonly);
            item.put("internal", internal);
            item.put("systemField", systemField);
            if (StringUtils.hasText(description)) {
                item.put("description", description);
            }
            return item;
        }
    }

    private record NodeFieldPermission(String fieldCode,
                                       String label,
                                       boolean readable,
                                       boolean writable,
                                       boolean required) {

        private String toJson() {
            return "{\"field\":\"" + escapeJson(fieldCode)
                    + "\",\"label\":\"" + escapeJson(label)
                    + "\",\"readable\":" + readable
                    + ",\"writable\":" + writable
                    + ",\"required\":" + required
                    + "}";
        }

        private String escapeJson(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
