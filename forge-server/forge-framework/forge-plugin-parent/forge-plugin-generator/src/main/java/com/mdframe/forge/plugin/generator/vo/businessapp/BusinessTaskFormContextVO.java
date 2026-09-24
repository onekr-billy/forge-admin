package com.mdframe.forge.plugin.generator.vo.businessapp;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务待办表单运行上下文。
 */
@Data
public class BusinessTaskFormContextVO {

    private Boolean configured = false;

    private String formType;

    private String taskId;

    private String objectCode;

    private String businessObjectName;

    private String businessSummary;

    private String configKey;

    private Long recordId;

    private String businessKey;

    private String processInstanceId;

    /** 低代码业务流程的不可变运行版本身份；历史代码流程可以为空。 */
    private Long processRunId;

    private String processDefKey;

    private String taskDefKey;

    private String formKey;

    private String formName;

    /** 应用页面级表单身份，便于待办端明确显示实际绑定页面。 */
    private String applicationId;

    private String pageId;

    private String pageCode;

    private String pageName;

    private String providerKey;

    private String formUrl;

    private String viewKey;

    private String editMode;

    private Integer gridCols;

    private String labelPlacement;

    private String labelWidth;

    private Map<String, Object> formRef = new LinkedHashMap<>();

    private List<Map<String, Object>> fields = new ArrayList<>();

    private List<Map<String, Object>> formAssets = new ArrayList<>();

    private List<Map<String, Object>> childrenConfig = new ArrayList<>();

    private List<Map<String, Object>> fieldPermissions = new ArrayList<>();

    private Map<String, Object> recordData = new LinkedHashMap<>();

    /**
     * 统一渲染协议版本。Phase 1 起 business-object 场景为 {@code "1"}；
     * 未产出 uiDocument 时可为 null，客户端继续走旧 fields 协议。
     */
    private String protocolVersion;

    /**
     * SAP uiData 风格的可渲染文档：sections + components + actions。
     * 与 {@link #fields}/{@link #recordData} 并存，便于 PC/H5 渐进切换。
     */
    private Map<String, Object> uiDocument = new LinkedHashMap<>();

    /**
     * Flow task form snapshot already loaded and authorized for this request.
     * The todo client reuses it instead of issuing a duplicate Flow request.
     */
    private Map<String, Object> taskFormInfo = new LinkedHashMap<>();

    private List<String> warnings = new ArrayList<>();

    private Boolean allowApprove;

    private Boolean allowDelegate;

    private Boolean allowReject;

    private Boolean allowRejectToStart;

    private Boolean allowReturn;

    private Boolean allowMultiReturn;

    private List<Map<String, Object>> returnTargets = new ArrayList<>();

    private Boolean allowDirectSend;

    private String returnSourceActivityId;

    private String returnSourceActivityName;

    private Boolean allowTerminate;

    private Boolean requireSignature;

    private Boolean requireComment;
}
