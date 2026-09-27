package com.mdframe.forge.plugin.generator.service.businessprocess;

import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessNode;
import com.mdframe.forge.plugin.generator.businessprocess.schema.BusinessProcessSchema;
import com.mdframe.forge.plugin.generator.businessprocess.validation.BusinessProcessSchemaValidator;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessNodeRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessRun;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessProcessVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessProcessVersionMapper;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunDetailVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps persisted process runs into API views and resolves published node display names. */
final class BusinessProcessRunViewAssembler {

    private BusinessProcessRunViewAssembler() {
    }

    static BusinessProcessRunVO toVo(AiBusinessProcessRun run, String processName) {
        BusinessProcessRunVO vo = new BusinessProcessRunVO();
        fillVo(vo, run, processName);
        return vo;
    }

    static BusinessProcessRunDetailVO toDetail(AiBusinessProcessRun run) {
        BusinessProcessRunDetailVO vo = new BusinessProcessRunDetailVO();
        fillVo(vo, run, null);
        vo.setFlowProcessInstanceId(run.getFlowProcessInstanceId());
        return vo;
    }

    static BusinessProcessRunDetailVO.NodeRunVO toNodeVo(AiBusinessProcessNodeRun nodeRun) {
        BusinessProcessRunDetailVO.NodeRunVO vo = new BusinessProcessRunDetailVO.NodeRunVO();
        vo.setId(stringId(nodeRun.getId()));
        vo.setRunId(stringId(nodeRun.getRunId()));
        vo.setNodeId(nodeRun.getNodeId());
        vo.setNodeType(nodeRun.getNodeType());
        vo.setAttemptNo(nodeRun.getAttemptNo());
        vo.setStatus(nodeRun.getStatus());
        vo.setCorrelationId(nodeRun.getCorrelationId());
        vo.setInputSummary(nodeRun.getInputSummary());
        vo.setOutputSummary(nodeRun.getOutputSummary());
        vo.setErrorCode(nodeRun.getErrorCode());
        vo.setErrorSummary(nodeRun.getErrorSummary());
        vo.setNextRetryTime(nodeRun.getNextRetryTime());
        vo.setStartTime(nodeRun.getStartTime());
        vo.setEndTime(nodeRun.getEndTime());
        vo.setCreateTime(nodeRun.getCreateTime());
        vo.setUpdateTime(nodeRun.getUpdateTime());
        return vo;
    }

    static void enrichNodeNames(Long tenantId, List<BusinessProcessRunVO> records,
                                BusinessProcessVersionMapper versionMapper,
                                BusinessProcessSchemaValidator schemaValidator) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, Map<String, String>> cache = new HashMap<>();
        for (BusinessProcessRunVO run : records) {
            String versionId = run.getProcessVersionId();
            if (versionId == null || versionId.isBlank()) {
                continue;
            }
            Map<String, String> nodeNameMap = cache.computeIfAbsent(versionId,
                    key -> loadNodeNameMap(tenantId, parseLong(key), versionMapper, schemaValidator));
            if (!nodeNameMap.isEmpty()) {
                run.setCurrentNodeName(nodeNameMap.getOrDefault(run.getCurrentNodeId(), run.getCurrentNodeId()));
            }
        }
    }

    static Map<String, String> loadNodeNameMap(Long tenantId, Long processVersionId,
                                               BusinessProcessVersionMapper versionMapper,
                                               BusinessProcessSchemaValidator schemaValidator) {
        if (tenantId == null || processVersionId == null) {
            return Collections.emptyMap();
        }
        AiBusinessProcessVersion version = versionMapper.selectPublishedVersionById(tenantId, processVersionId);
        if (version == null || version.getSchemaJson() == null || version.getSchemaJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            BusinessProcessSchema schema = schemaValidator.normalize(version.getSchemaJson());
            Map<String, String> map = new LinkedHashMap<>();
            List<BusinessProcessNode> nodes = schema.getNodes() != null
                    ? schema.getNodes() : Collections.emptyList();
            for (BusinessProcessNode node : nodes) {
                if (node.getId() != null) {
                    map.put(node.getId(), node.getName() != null ? node.getName() : node.getId());
                }
            }
            return map;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static void fillVo(BusinessProcessRunVO vo, AiBusinessProcessRun run, String processName) {
        vo.setId(stringId(run.getId()));
        vo.setApplicationId(stringId(run.getApplicationId()));
        vo.setProcessId(stringId(run.getProcessId()));
        vo.setProcessVersionId(stringId(run.getProcessVersionId()));
        vo.setProcessCode(run.getProcessCode());
        vo.setProcessName(processName);
        vo.setSubjectObjectCode(run.getSubjectObjectCode());
        vo.setSubjectRecordId(run.getSubjectRecordId());
        vo.setBusinessKey(run.getBusinessKey());
        vo.setTriggerType(run.getTriggerType());
        vo.setActorType(run.getActorType());
        vo.setActorUserId(stringId(run.getActorUserId()));
        vo.setActiveOrgId(stringId(run.getActiveOrgId()));
        vo.setStatus(run.getStatus());
        vo.setCurrentNodeId(run.getCurrentNodeId());
        vo.setRetryCount(run.getRetryCount());
        vo.setErrorCode(run.getErrorCode());
        vo.setErrorSummary(run.getErrorSummary());
        vo.setStartTime(run.getStartTime());
        vo.setEndTime(run.getEndTime());
        vo.setCreateTime(run.getCreateTime());
        vo.setUpdateTime(run.getUpdateTime());
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stringId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
