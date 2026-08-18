package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessEdge;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContext;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessValidationContextResolver;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplication;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcess;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessSchemaDTO;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationObjectMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.service.businessapp.BusinessNamingService;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationObjectVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessObjectProcessVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessFlowModelVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessValidationVO;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.SessionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 应用级业务流程定义控制面。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessProcessService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int GENERATED_CODE_ATTEMPTS = 1000;
    private static final Pattern PROCESS_CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{2,127}");
    private static final Set<String> DRAFT_SAVE_BLOCKING_CODES = Set.of(
            "SCHEMA_VERSION_UNSUPPORTED",
            "PROCESS_CODE_INVALID",
            "PROCESS_CODE_MISMATCH",
            "SUBJECT_REQUIRED",
            "SUBJECT_OBJECT_ID_INVALID",
            "SUBJECT_OBJECT_CODE_REQUIRED",
            "SUBJECT_OBJECT_UNAVAILABLE",
            "SUBJECT_OBJECT_MISMATCH",
            "OBJECT_DEPENDENCY_UNAVAILABLE",
            "ACTION_OBJECT_UNAVAILABLE",
            "SENSITIVE_KEY",
            "FREE_URL_FORBIDDEN");

    private final BusinessProcessMapper processMapper;
    private final BusinessProcessVersionMapper versionMapper;
    private final BusinessProcessRunMapper runMapper;
    private final BusinessApplicationMapper applicationMapper;
    private final BusinessApplicationObjectMapper applicationObjectMapper;
    private final BusinessProcessSchemaValidator schemaValidator;
    private final BusinessProcessValidationContextResolver validationContextResolver;
    private final BusinessNamingService namingService;

    public Page<BusinessProcessVO> page(Integer pageNum,
                                        Integer pageSize,
                                        String applicationId,
                                        String keyword,
                                        Integer status,
                                        String designStatus) {
        Long tenantId = requireTenantId();
        Long appId = requireId(applicationId, "业务应用ID");
        requireActiveApplication(tenantId, appId);
        Page<AiBusinessProcess> source = processMapper.selectProcessPage(
                new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize)),
                tenantId,
                appId,
                StringUtils.trimToNull(keyword),
                normalizeOptionalStatus(status),
                normalizeDesignStatus(designStatus));
        Page<BusinessProcessVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(source.getRecords().stream().map(process -> toVO(process, false)).toList());
        return result;
    }

    public BusinessProcessVO detail(Long processId) {
        return toVO(requireProcess(requireTenantId(), processId), false);
    }

    public List<BusinessObjectProcessVO> listByObjectCode(String objectCode) {
        Long tenantId = requireTenantId();
        String normalizedObjectCode = StringUtils.trimToNull(objectCode);
        if (normalizedObjectCode == null) {
            throw new BusinessException("业务对象编码不能为空");
        }
        List<BusinessObjectProcessVO> result = safeList(processMapper.selectBySubjectObjectCode(tenantId, normalizedObjectCode));
        result.forEach(this::extractStartNodeType);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO create(BusinessProcessDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务流程不能为空");
        }
        Long tenantId = requireTenantId();
        Long applicationId = requireId(dto.getApplicationId(), "业务应用ID");
        requireActiveApplication(tenantId, applicationId);
        BusinessApplicationObjectVO subject = requireApplicationObject(
                tenantId, applicationId, requireId(dto.getSubjectObjectId(), "主业务对象ID"));
        String processName = requireProcessName(dto.getProcessName());
        String processCode = resolveCreateCode(
                tenantId, applicationId, dto.getProcessCode(), processName);
        BusinessProcessSchema schema = initialSchema(processCode, subject);
        AiBusinessProcess process = new AiBusinessProcess();
        process.setTenantId(tenantId);
        process.setApplicationId(applicationId);
        process.setProcessCode(processCode);
        process.setProcessName(processName);
        process.setProcessDescription(normalizeDescription(dto.getProcessDescription()));
        process.setSubjectObjectId(subject.getObjectId());
        process.setSubjectObjectCode(subject.getObjectCode());
        process.setDraftSchemaJson(schemaValidator.canonicalJson(schema));
        process.setDraftSchemaHash(schemaValidator.schemaHash(schema));
        process.setDesignStatus("DRAFT");
        process.setCurrentVersion(0);
        process.setPublishedVersion(null);
        process.setStatus(normalizeStatus(dto.getStatus()));
        process.setLegacySourceType(null);
        process.setLegacySourceId(null);
        applyAudit(process);
        insertProcess(process, "流程编码已存在，请刷新后重试");
        applicationMapper.markChanged(tenantId, applicationId);
        return toVO(process, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO copy(Long sourceProcessId, BusinessProcessDTO dto) {
        Long tenantId = requireTenantId();
        AiBusinessProcess source = requireProcess(tenantId, sourceProcessId);
        BusinessProcessDTO request = dto == null ? new BusinessProcessDTO() : dto;
        assertSameApplicationAndSubject(source, request);
        String processName = StringUtils.defaultIfBlank(
                StringUtils.trimToNull(request.getProcessName()), source.getProcessName() + "副本");
        processName = requireProcessName(processName);
        String processCode = resolveCopyCode(
                tenantId, source.getApplicationId(), source.getProcessCode(), request.getProcessCode());
        BusinessProcessSchema copiedSchema = rebuildGraphIds(
                parseSchema(source.getDraftSchemaJson()), processCode);
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, source.getApplicationId(), processCode, copiedSchema);
        assertDraftSaveAllowed(schemaValidator.validate(copiedSchema, context));

        AiBusinessProcess copied = new AiBusinessProcess();
        copied.setTenantId(tenantId);
        copied.setApplicationId(source.getApplicationId());
        copied.setProcessCode(processCode);
        copied.setProcessName(processName);
        copied.setProcessDescription(request.getProcessDescription() == null
                ? source.getProcessDescription() : normalizeDescription(request.getProcessDescription()));
        copied.setSubjectObjectId(source.getSubjectObjectId());
        copied.setSubjectObjectCode(source.getSubjectObjectCode());
        copied.setDraftSchemaJson(schemaValidator.canonicalJson(copiedSchema));
        copied.setDraftSchemaHash(schemaValidator.schemaHash(copiedSchema));
        copied.setDesignStatus("DRAFT");
        copied.setCurrentVersion(0);
        copied.setPublishedVersion(null);
        copied.setStatus(normalizeStatus(request.getStatus()));
        copied.setLegacySourceType(null);
        copied.setLegacySourceId(null);
        applyAudit(copied);
        insertProcess(copied, "流程副本编码已存在，请刷新后重试");
        applicationMapper.markChanged(tenantId, copied.getApplicationId());
        return toVO(copied, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO update(BusinessProcessDTO dto) {
        if (dto == null) {
            throw new BusinessException("业务流程不能为空");
        }
        Long tenantId = requireTenantId();
        Long processId = requireId(dto.getId(), "业务流程ID");
        AiBusinessProcess process = requireProcess(tenantId, processId);
        assertSameApplicationAndSubject(process, dto);
        String requestedCode = StringUtils.trimToNull(dto.getProcessCode());
        if (requestedCode != null && !requestedCode.equals(process.getProcessCode())) {
            throw new BusinessException("流程编码创建后不能修改");
        }
        String processName = requireProcessName(dto.getProcessName());
        String description = normalizeDescription(dto.getProcessDescription());
        int updated = processMapper.updateBasicInfo(
                tenantId, processId, processName, description, requireUserId());
        if (updated != 1) {
            throw conflict("流程基础信息已变化，请刷新后重试");
        }
        applicationMapper.markChanged(tenantId, process.getApplicationId());
        process.setProcessName(processName);
        process.setProcessDescription(description);
        return toVO(process, false);
    }

    public BusinessProcessVO getDesigner(Long processId) {
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        BusinessProcessSchema schema = parseSchema(process.getDraftSchemaJson());
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, process.getApplicationId(), process.getProcessCode(), schema);
        BusinessProcessValidationVO validation = schemaValidator.validate(schema, context);
        BusinessProcessVO result = toVO(process, false);
        result.setBusinessProcessJson(schema);
        result.setValidation(validation);
        return result;
    }

    public List<BusinessProcessFlowModelVO> availableFlowModels(Long processId) {
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        return validationContextResolver.resolveAvailableFlowModels(
                tenantId, process.getApplicationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessVO saveSchema(Long processId, BusinessProcessSchemaDTO dto) {
        if (dto == null || dto.getBusinessProcessJson() == null) {
            throw new BusinessException("业务流程协议不能为空");
        }
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        String expectedHash = requireSchemaHash(dto.getExpectedSchemaHash());
        BusinessProcessSchema schema = parseSchema(dto.getBusinessProcessJson().toString());
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, process.getApplicationId(), process.getProcessCode(), schema);
        BusinessProcessValidationVO validation = schemaValidator.validate(schema, context);
        assertDraftSaveAllowed(validation);
        Long subjectObjectId = requireId(schema.getSubject().getObjectId(), "主业务对象ID");
        String schemaJson = schemaValidator.canonicalJson(schema);
        String schemaHash = schemaValidator.schemaHash(schema);
        String designStatus = resolveDesignStatus(process, schemaHash, validation.isValid());
        int updated = processMapper.updateDraftSchema(
                tenantId,
                processId,
                schemaJson,
                schemaHash,
                expectedHash,
                subjectObjectId,
                schema.getSubject().getObjectCode(),
                designStatus,
                requireUserId());
        if (updated != 1) {
            throw conflict("业务流程草稿已被其他操作更新，请刷新后重试");
        }
        applicationMapper.markChanged(tenantId, process.getApplicationId());
        process.setDraftSchemaJson(schemaJson);
        process.setDraftSchemaHash(schemaHash);
        process.setSubjectObjectId(subjectObjectId);
        process.setSubjectObjectCode(schema.getSubject().getObjectCode());
        process.setDesignStatus(designStatus);
        BusinessProcessVO result = toVO(process, false);
        result.setBusinessProcessJson(schema);
        result.setValidation(validation);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessProcessValidationVO validate(Long processId) {
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        BusinessProcessSchema schema = parseSchema(process.getDraftSchemaJson());
        BusinessProcessValidationContext context = validationContextResolver.resolve(
                tenantId, process.getApplicationId(), process.getProcessCode(), schema);
        BusinessProcessValidationVO validation = schemaValidator.validate(schema, context);
        String designStatus = resolveDesignStatus(
                process, process.getDraftSchemaHash(), validation.isValid());
        int updated = processMapper.updateDesignStatus(
                tenantId, processId, process.getDraftSchemaHash(), designStatus, requireUserId());
        if (updated != 1) {
            throw conflict("校验期间业务流程草稿已变化，请刷新后重试");
        }
        return validation;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long processId, Integer status) {
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        int updated = processMapper.updateStatus(
                tenantId, processId, normalizeStatus(status), requireUserId());
        if (updated != 1) {
            throw conflict("流程状态已变化，请刷新后重试");
        }
        applicationMapper.markChanged(tenantId, process.getApplicationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void logicalDelete(Long processId) {
        Long tenantId = requireTenantId();
        AiBusinessProcess process = requireProcess(tenantId, processId);
        if (value(runMapper.countByProcessId(tenantId, processId)) > 0) {
            throw new BusinessException("业务流程存在运行记录，不能删除；可以先停用以阻止新触发");
        }
        if (value(versionMapper.countActiveReferences(tenantId, processId)) > 0) {
            throw new BusinessException("业务流程仍有有效发布版本引用，不能删除；可以先停用");
        }
        int updated = processMapper.logicalDelete(tenantId, processId, requireUserId());
        if (updated != 1) {
            throw conflict("流程已被删除或发生并发变化，请刷新后重试");
        }
        applicationMapper.markChanged(tenantId, process.getApplicationId());
    }

    private BusinessProcessSchema initialSchema(String processCode, BusinessApplicationObjectVO subject) {
        BusinessProcessSchema schema = new BusinessProcessSchema();
        schema.setSchemaVersion(BusinessProcessSchemaValidator.SCHEMA_VERSION);
        schema.setProcessCode(processCode);
        BusinessProcessSchema.Subject schemaSubject = new BusinessProcessSchema.Subject();
        schemaSubject.setObjectId(String.valueOf(subject.getObjectId()));
        schemaSubject.setObjectCode(subject.getObjectCode());
        schemaSubject.setRecordIdSource("RUNTIME_RECORD");
        schema.setSubject(schemaSubject);

        BusinessProcessNode start = new BusinessProcessNode();
        start.setId("start_manual");
        start.setType("START_MANUAL");
        start.setName("手动开始");
        start.setConfig(new LinkedHashMap<>(Map.of(
                "positions", List.of("ROW", "DETAIL"),
                "permission", "ai:businessProcess:start")));
        BusinessProcessNode end = new BusinessProcessNode();
        end.setId("end_success");
        end.setType("END");
        end.setName("完成");
        end.setConfig(new LinkedHashMap<>(Map.of("result", "SUCCESS")));
        schema.setNodes(new ArrayList<>(List.of(start, end)));

        BusinessProcessEdge edge = new BusinessProcessEdge();
        edge.setId("edge_start_end");
        edge.setSource(start.getId());
        edge.setTarget(end.getId());
        edge.setSourcePort("NEXT");
        schema.setEdges(new ArrayList<>(List.of(edge)));

        BusinessProcessSchema.RetryPolicy retry = new BusinessProcessSchema.RetryPolicy();
        retry.setMode("LIMITED");
        retry.setMaxAttempts(3);
        retry.setBackoffSeconds(new ArrayList<>(List.of(30, 120, 600)));
        BusinessProcessSchema.Policies policies = new BusinessProcessSchema.Policies();
        policies.setApprovalConcurrency("ONE_ACTIVE_PER_BUSINESS_KEY");
        policies.setMaxSubProcessDepth(5);
        policies.setRetry(retry);
        schema.setPolicies(policies);
        BusinessProcessSchema.Dependencies dependencies = new BusinessProcessSchema.Dependencies();
        dependencies.setObjects(new ArrayList<>(List.of(subject.getObjectCode())));
        schema.setDependencies(dependencies);
        return schema;
    }

    private BusinessProcessSchema rebuildGraphIds(BusinessProcessSchema source, String processCode) {
        source.setProcessCode(processCode);
        source.setMetadata(new LinkedHashMap<>());
        Map<String, String> nodeIds = new LinkedHashMap<>();
        for (BusinessProcessNode node : source.getNodes()) {
            String newId = randomGraphId("node");
            nodeIds.put(node.getId(), newId);
            node.setId(newId);
        }
        for (BusinessProcessEdge edge : source.getEdges()) {
            edge.setId(randomGraphId("edge"));
            edge.setSource(nodeIds.get(edge.getSource()));
            edge.setTarget(nodeIds.get(edge.getTarget()));
        }
        return source;
    }

    private String randomGraphId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveCreateCode(Long tenantId,
                                     Long applicationId,
                                     String requestedCode,
                                     String processName) {
        String requested = StringUtils.trimToNull(requestedCode);
        if (requested != null) {
            validateProcessCode(requested);
            assertProcessCodeAvailable(tenantId, applicationId, requested);
            return requested;
        }
        String base = namingService.generateObjectCode(processName);
        if (!PROCESS_CODE_PATTERN.matcher(base).matches()) {
            base = "business_process";
        }
        return nextAvailableCode(tenantId, applicationId, base);
    }

    private String resolveCopyCode(Long tenantId,
                                   Long applicationId,
                                   String sourceCode,
                                   String requestedCode) {
        String requested = StringUtils.trimToNull(requestedCode);
        if (requested != null) {
            validateProcessCode(requested);
            if (requested.equals(sourceCode)) {
                throw new BusinessException("流程副本必须使用新的流程编码");
            }
            assertProcessCodeAvailable(tenantId, applicationId, requested);
            return requested;
        }
        return nextAvailableCode(tenantId, applicationId,
                StringUtils.left(sourceCode + "_copy", 128));
    }

    private String nextAvailableCode(Long tenantId, Long applicationId, String baseCode) {
        for (int sequence = 1; sequence <= GENERATED_CODE_ATTEMPTS; sequence++) {
            String suffix = sequence == 1 ? "" : "_" + sequence;
            String candidate = StringUtils.left(baseCode, 128 - suffix.length())
                    .replaceAll("_+$", "") + suffix;
            validateProcessCode(candidate);
            if (processMapper.selectActiveByCode(tenantId, applicationId, candidate) == null) {
                return candidate;
            }
        }
        throw new BusinessException("无法生成唯一流程编码，请在高级设置中填写流程编码");
    }

    private void assertProcessCodeAvailable(Long tenantId, Long applicationId, String processCode) {
        if (processMapper.selectActiveByCode(tenantId, applicationId, processCode) != null) {
            throw new BusinessException("流程编码已存在: " + processCode);
        }
    }

    private void validateProcessCode(String processCode) {
        if (!PROCESS_CODE_PATTERN.matcher(StringUtils.defaultString(processCode)).matches()) {
            throw new BusinessException("流程编码必须使用小写字母、数字和下划线，且长度为3-128个字符");
        }
    }

    private void assertSameApplicationAndSubject(AiBusinessProcess process, BusinessProcessDTO dto) {
        String applicationId = StringUtils.trimToNull(dto.getApplicationId());
        if (applicationId != null
                && !process.getApplicationId().equals(requireId(applicationId, "业务应用ID"))) {
            throw new BusinessException("流程创建后不能移动到其它业务应用");
        }
        String subjectObjectId = StringUtils.trimToNull(dto.getSubjectObjectId());
        if (subjectObjectId != null
                && !process.getSubjectObjectId().equals(requireId(subjectObjectId, "主业务对象ID"))) {
            throw new BusinessException("请在业务流程设计器中修改主业务对象并按草稿摘要保存");
        }
    }

    private AiBusinessApplication requireActiveApplication(Long tenantId, Long applicationId) {
        AiBusinessApplication application = applicationMapper.selectEntityById(tenantId, applicationId);
        if (application == null || !Integer.valueOf(1).equals(application.getStatus())) {
            throw new BusinessException("业务应用不存在或已停用");
        }
        return application;
    }

    private BusinessApplicationObjectVO requireApplicationObject(Long tenantId,
                                                                 Long applicationId,
                                                                 Long objectId) {
        return safeList(applicationObjectMapper.selectByApplicationId(tenantId, applicationId)).stream()
                .filter(object -> object != null
                        && objectId.equals(object.getObjectId())
                        && Integer.valueOf(1).equals(object.getObjectStatus())
                        && StringUtils.isNotBlank(object.getObjectCode()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("主业务对象不属于当前应用或已停用"));
    }

    private AiBusinessProcess requireProcess(Long tenantId, Long processId) {
        if (processId == null || processId <= 0) {
            throw new BusinessException("业务流程ID不能为空");
        }
        AiBusinessProcess process = processMapper.selectActiveById(tenantId, processId);
        if (process == null) {
            throw new BusinessException("业务流程不存在、所属应用已停用或主业务对象已失效");
        }
        return process;
    }

    private BusinessProcessSchema parseSchema(String schemaJson) {
        try {
            return schemaValidator.normalize(schemaJson);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, exception.getMessage(), exception);
        }
    }

    /**
     * 从 draftSchemaJson 中提取第一个 START_ 开头的节点类型，填充到 startNodeType 字段，
     * 然后清理 draftSchemaJson 以避免序列化时暴露完整画布 JSON。
     */
    private void extractStartNodeType(BusinessObjectProcessVO vo) {
        String json = vo.getDraftSchemaJson();
        vo.setDraftSchemaJson(null);
        if (StringUtils.isBlank(json)) {
            return;
        }
        try {
            BusinessProcessSchema schema = parseSchema(json);
            if (schema != null && schema.getNodes() != null) {
                schema.getNodes().stream()
                        .map(BusinessProcessNode::getType)
                        .filter(type -> type != null && type.startsWith("START_"))
                        .findFirst()
                        .ifPresent(vo::setStartNodeType);
            }
        } catch (Exception exception) {
            log.debug("无法解析业务流程草稿 JSON 提取 startNodeType（processCode={}）：{}",
                    vo.getProcessCode(), exception.getMessage());
        }
    }

    private void assertDraftSaveAllowed(BusinessProcessValidationVO validation) {
        List<BusinessProcessValidationVO.ValidationIssueVO> blocked = validation.getIssues().stream()
                .filter(issue -> "ERROR".equals(issue.getLevel()))
                .filter(issue -> DRAFT_SAVE_BLOCKING_CODES.contains(issue.getCode()))
                .toList();
        if (!blocked.isEmpty()) {
            throw new BusinessException(422, "业务流程草稿包含跨应用引用、身份错误或禁止保存的敏感配置", validation);
        }
    }

    private String resolveDesignStatus(AiBusinessProcess process,
                                       String schemaHash,
                                       boolean valid) {
        if (process.getPublishedVersion() == null) {
            return valid ? "VALIDATED" : "DRAFT";
        }
        AiBusinessProcessVersion published = versionMapper.selectPublishedVersion(
                process.getTenantId(), process.getId(), process.getPublishedVersion());
        if (published != null && StringUtils.equals(published.getSchemaHash(), schemaHash)) {
            return "PUBLISHED";
        }
        return "CHANGED";
    }

    private void insertProcess(AiBusinessProcess process, String conflictMessage) {
        try {
            if (processMapper.insert(process) != 1) {
                throw new BusinessException("业务流程保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, conflictMessage, exception);
        }
    }

    private BusinessProcessVO toVO(AiBusinessProcess process, boolean includeSchema) {
        BusinessProcessVO vo = new BusinessProcessVO();
        vo.setId(text(process.getId()));
        vo.setApplicationId(text(process.getApplicationId()));
        vo.setProcessCode(process.getProcessCode());
        vo.setProcessName(process.getProcessName());
        vo.setProcessDescription(process.getProcessDescription());
        vo.setSubjectObjectId(text(process.getSubjectObjectId()));
        vo.setSubjectObjectCode(process.getSubjectObjectCode());
        vo.setDraftSchemaHash(process.getDraftSchemaHash());
        vo.setDesignStatus(process.getDesignStatus());
        vo.setCurrentVersion(process.getCurrentVersion());
        vo.setPublishedVersion(process.getPublishedVersion());
        vo.setStatus(process.getStatus());
        vo.setCreateBy(text(process.getCreateBy()));
        vo.setCreateTime(process.getCreateTime());
        vo.setUpdateBy(text(process.getUpdateBy()));
        vo.setUpdateTime(process.getUpdateTime());
        if (includeSchema) {
            vo.setBusinessProcessJson(parseSchema(process.getDraftSchemaJson()));
        }
        return vo;
    }

    private void applyAudit(AiBusinessProcess process) {
        Long userId = requireUserId();
        process.setCreateBy(userId);
        process.setUpdateBy(userId);
        process.setCreateDept(SessionHelper.getActiveOrgId());
    }

    private String requireProcessName(String value) {
        String name = StringUtils.trimToNull(value);
        if (name == null) {
            throw new BusinessException("流程名称不能为空");
        }
        if (name.length() > 128) {
            throw new BusinessException("流程名称长度不能超过128个字符");
        }
        return name;
    }

    private String normalizeDescription(String value) {
        String description = StringUtils.trimToNull(value);
        if (description != null && description.length() > 500) {
            throw new BusinessException("流程说明长度不能超过500个字符");
        }
        return description;
    }

    private Integer normalizeStatus(Integer status) {
        int value = status == null ? 1 : status;
        if (value != 0 && value != 1) {
            throw new BusinessException("状态值不正确");
        }
        return value;
    }

    private Integer normalizeOptionalStatus(Integer status) {
        return status == null ? null : normalizeStatus(status);
    }

    private String normalizeDesignStatus(String designStatus) {
        String value = StringUtils.trimToNull(designStatus);
        if (value == null) {
            return null;
        }
        value = value.toUpperCase(Locale.ROOT);
        if (!Set.of("DRAFT", "VALIDATED", "PUBLISHED", "CHANGED").contains(value)) {
            throw new BusinessException("流程设计状态不正确");
        }
        return value;
    }

    private String requireSchemaHash(String value) {
        String hash = StringUtils.trimToNull(value);
        if (hash == null || !hash.matches("^[a-f0-9]{64}$")) {
            throw new BusinessException("草稿基线摘要格式不正确");
        }
        return hash;
    }

    private Long requireId(String value, String label) {
        String id = StringUtils.trimToNull(value);
        if (id == null || !id.matches("^[0-9]{1,19}$")) {
            throw new BusinessException(label + "不能为空或格式不正确");
        }
        try {
            long parsed = Long.parseLong(id);
            if (parsed <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(label + "不能为空或格式不正确");
        }
    }

    private Long requireTenantId() {
        Long tenantId;
        try {
            tenantId = SessionHelper.getTenantId();
        } catch (Exception exception) {
            log.debug("业务流程控制面未读取到有效租户上下文", exception);
            tenantId = null;
        }
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException("未获取到有效租户上下文");
        }
        return tenantId;
    }

    private Long requireUserId() {
        Long userId;
        try {
            userId = SessionHelper.getUserId();
        } catch (Exception exception) {
            log.debug("业务流程控制面未读取到有效操作用户", exception);
            userId = null;
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("未获取到有效操作用户");
        }
        return userId;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private String text(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
