package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessManualStartDTO;
import com.mdframe.forge.plugin.generator.dto.businessprocess.BusinessProcessRunQueryDTO;
import com.mdframe.forge.plugin.generator.service.businessprocess.BusinessProcessOrchestrator;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunDetailVO;
import com.mdframe.forge.plugin.generator.vo.businessprocess.BusinessProcessRunVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应用级业务流程运行接口。
 */
@RestController
@RequestMapping("/ai/business/process")
@RequiredArgsConstructor
@ApiDecrypt
@ApiEncrypt
public class BusinessProcessRuntimeController {

    private final BusinessProcessOrchestrator orchestrator;

    @GetMapping("/runtime/{applicationCode}/{processCode}/start-config")
    @SaCheckPermission("ai:businessProcess:start")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "查询业务流程发起配置")
    public RespInfo<Map<String, Object>> startConfig(
            @PathVariable String applicationCode,
            @PathVariable String processCode) {
        return RespInfo.success(orchestrator.startConfig(applicationCode, processCode));
    }

    @PostMapping("/runtime/{applicationCode}/{processCode}/start")
    @SaCheckPermission("ai:businessProcess:start")
    @OperationLog(module = "业务流程", type = OperationType.ADD, desc = "手动启动已发布业务流程")
    public RespInfo<BusinessProcessRunVO> start(
            @PathVariable String applicationCode,
            @PathVariable String processCode,
            @Valid @RequestBody BusinessProcessManualStartDTO dto) {
        return RespInfo.success(orchestrator.start(applicationCode, processCode, dto));
    }

    @GetMapping("/run/page")
    @SaCheckPermission("ai:businessProcess:run:list")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "分页查询业务流程运行记录")
    public RespInfo<Page<BusinessProcessRunVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) String subjectObjectCode,
            @RequestParam(required = false) String subjectRecordId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType) {
        BusinessProcessRunQueryDTO query = new BusinessProcessRunQueryDTO();
        query.setApplicationId(applicationId);
        query.setProcessId(processId);
        query.setSubjectObjectCode(subjectObjectCode);
        query.setSubjectRecordId(subjectRecordId);
        query.setStatus(status);
        query.setTriggerType(triggerType);
        return RespInfo.success(orchestrator.page(pageNum, pageSize, query));
    }

    @GetMapping("/run/{id}")
    @SaCheckPermission("ai:businessProcess:run:detail")
    @OperationLog(module = "业务流程", type = OperationType.QUERY, desc = "查询业务流程运行详情")
    public RespInfo<BusinessProcessRunDetailVO> detail(@PathVariable Long id) {
        return RespInfo.success(orchestrator.detail(id));
    }

    @PostMapping("/run/{id}/retry")
    @SaCheckPermission("ai:businessProcess:run:retry")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "重试失败的业务流程运行")
    public RespInfo<BusinessProcessRunVO> retry(@PathVariable Long id) {
        return RespInfo.success(orchestrator.retry(id));
    }

    @PostMapping("/run/{id}/cancel")
    @SaCheckPermission("ai:businessProcess:run:cancel")
    @OperationLog(module = "业务流程", type = OperationType.UPDATE, desc = "取消尚未结束的业务流程运行")
    public RespInfo<BusinessProcessRunVO> cancel(@PathVariable Long id) {
        return RespInfo.success(orchestrator.cancel(id));
    }
}
