package com.mdframe.forge.plugin.generator.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditEventQueryDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditFieldQueryDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditPolicyDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditRevealDTO;
import com.mdframe.forge.plugin.generator.dto.audit.DataAuditScopeDTO;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditFilterOptionService;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditPolicyService;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditQueryService;
import com.mdframe.forge.plugin.generator.service.audit.DataAuditScopeService;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditEventVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditFieldVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditFilterOptionsVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditPolicyVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditRevealVO;
import com.mdframe.forge.plugin.generator.vo.audit.DataAuditScopeItemVO;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.crypto.ApiEncrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/data-audit")
@RequiredArgsConstructor
@ApiEncrypt
public class DataAuditController {

    private final DataAuditQueryService queryService;
    private final DataAuditPolicyService policyService;
    private final DataAuditScopeService scopeService;
    private final DataAuditFilterOptionService filterOptionService;

    @GetMapping("/record/{objectId}/{recordId}/page")
    @SaCheckPermission("ai:dataAudit:record")
    public RespInfo<Page<DataAuditEventVO>> recordPage(@PathVariable Long objectId,
                                                       @PathVariable String recordId,
                                                       DataAuditEventQueryDTO query,
                                                       @RequestParam(defaultValue = "1") Integer pageNum,
                                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return RespInfo.success(queryService.pageRecordEvents(objectId, recordId, query, pageNum, pageSize));
    }

    @GetMapping("/page")
    @SaCheckPermission("ai:dataAudit:list")
    public RespInfo<Page<DataAuditEventVO>> page(DataAuditEventQueryDTO query,
                                                 @RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        return RespInfo.success(queryService.pageAdminEvents(query, pageNum, pageSize));
    }

    @GetMapping("/filter-options")
    @SaCheckPermission("ai:dataAudit:list")
    public RespInfo<DataAuditFilterOptionsVO> filterOptions() {
        return RespInfo.success(filterOptionService.listCurrentUserOptions());
    }

    @GetMapping("/filter-options/objects/{objectId}/fields")
    @SaCheckPermission("ai:dataAudit:record")
    public RespInfo<List<DataAuditFilterOptionsVO.FieldOption>> objectFieldOptions(@PathVariable Long objectId) {
        return RespInfo.success(filterOptionService.listObjectFields(objectId));
    }

    @GetMapping("/{eventId}")
    @SaCheckPermission(value = {"ai:dataAudit:record", "ai:dataAudit:list", "ai:dataAudit:detail"}, mode = SaMode.OR)
    public RespInfo<DataAuditEventVO> detail(@PathVariable Long eventId,
                                             @RequestParam(required = false) String accessMode,
                                             @RequestParam(required = false) String taskId) {
        return RespInfo.success(queryService.getEvent(eventId, accessMode, taskId));
    }

    @GetMapping("/{eventId}/fields/page")
    @SaCheckPermission(value = {"ai:dataAudit:record", "ai:dataAudit:list", "ai:dataAudit:detail"}, mode = SaMode.OR)
    public RespInfo<Page<DataAuditFieldVO>> fields(@PathVariable Long eventId,
                                                   DataAuditFieldQueryDTO query,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        return RespInfo.success(queryService.pageFields(eventId, query, pageNum, pageSize));
    }

    @ApiDecrypt
    @PostMapping("/{eventId}/fields/{fieldId}/reveal")
    @SaCheckPermission("ai:dataAudit:sensitive")
    @OperationLog(module = "数据变更审计", type = OperationType.OTHER, desc = "查看敏感原值",
            saveRequestParams = false, saveResponseResult = false)
    public RespInfo<DataAuditRevealVO> reveal(@PathVariable Long eventId,
                                              @PathVariable Long fieldId,
                                              @RequestBody DataAuditRevealDTO dto) {
        return RespInfo.success(queryService.reveal(eventId, fieldId, dto));
    }

    @GetMapping("/policy/{objectId}")
    @SaCheckPermission("ai:dataAudit:config")
    public RespInfo<DataAuditPolicyVO> policy(@PathVariable Long objectId) {
        return RespInfo.success(policyService.getPolicy(objectId));
    }

    @ApiDecrypt
    @PutMapping("/policy/{objectId}")
    @SaCheckPermission("ai:dataAudit:config")
    @OperationLog(module = "数据变更审计", type = OperationType.UPDATE, desc = "更新数据审计策略",
            saveRequestParams = false, saveResponseResult = false)
    public RespInfo<DataAuditPolicyVO> updatePolicy(@PathVariable Long objectId,
                                                    @RequestBody DataAuditPolicyDTO dto) {
        return RespInfo.success(policyService.updatePolicy(objectId, dto));
    }

    @GetMapping("/scope/{roleId}")
    @SaCheckPermission("ai:dataAudit:scope")
    public RespInfo<List<DataAuditScopeItemVO>> scope(@PathVariable Long roleId) {
        return RespInfo.success(scopeService.listByRole(roleId));
    }

    @ApiDecrypt
    @PutMapping("/scope/{roleId}")
    @SaCheckPermission("ai:dataAudit:scope")
    @OperationLog(module = "数据变更审计", type = OperationType.UPDATE, desc = "更新审计对象授权",
            saveRequestParams = false, saveResponseResult = false)
    public RespInfo<List<DataAuditScopeItemVO>> updateScope(@PathVariable Long roleId,
                                                            @RequestBody DataAuditScopeDTO dto) {
        return RespInfo.success(scopeService.replaceRoleScope(roleId, dto));
    }
}
