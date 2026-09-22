package com.mdframe.forge.admin.integration.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.admin.integration.dto.ApplicationConnectionOption;
import com.mdframe.forge.admin.integration.dto.ApplicationIntegrationConfig;
import com.mdframe.forge.admin.integration.service.ApplicationIntegrationService;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapability;
import com.mdframe.forge.plugin.capability.controlplane.domain.AiCapabilityGrant;
import com.mdframe.forge.plugin.capability.controlplane.dto.CapabilityGrantCreateDTO;
import com.mdframe.forge.plugin.capability.controlplane.mapper.AiCapabilityGrantMapper;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityClientService;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityGrantService;
import com.mdframe.forge.plugin.capability.controlplane.service.CapabilityInvocationAuditService;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityClientVO;
import com.mdframe.forge.plugin.capability.controlplane.vo.CapabilityInvocationDetailVO;
import com.mdframe.forge.plugin.capability.flowaction.publish.FlowActionCapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.flowaction.publish.FlowActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.secureaction.publish.BusinessActionCapabilityPublisher;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityPublishDTO;
import com.mdframe.forge.plugin.capability.secureaction.system.SystemServiceCapabilityPublisher;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.PageQuery;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/business/application/{appId}/integrations")
@RequiredArgsConstructor
public class ApplicationIntegrationController {
    private final ApplicationIntegrationService integrations;
    private final FlowActionCapabilityPublisher flowPublisher;
    private final BusinessActionCapabilityPublisher actionPublisher;
    private final SystemServiceCapabilityPublisher systemPublisher;
    private final CapabilityGrantService grants;
    private final AiCapabilityGrantMapper grantMapper;
    private final CapabilityClientService clients;
    private final CapabilityInvocationAuditService audit;

    @GetMapping("/collaboration")
    @SaCheckPermission({"ai:businessApplication:list", "system:collaboration:connection:list"})
    public RespInfo<ApplicationIntegrationConfig> config(@PathVariable Long appId) {
        return RespInfo.success(integrations.config(appId));
    }

    @GetMapping("/connections")
    @SaCheckPermission({"ai:businessApplication:list", "system:collaboration:connection:list"})
    public RespInfo<List<ApplicationConnectionOption>> connections(@PathVariable Long appId) {
        return RespInfo.success(integrations.connections(appId));
    }

    @PutMapping("/collaboration")
    @ApiDecrypt
    @SaCheckPermission({"ai:businessApplication:edit", "system:collaboration:connection:update"})
    @OperationLog(module = "应用集成", type = OperationType.UPDATE, desc = "保存应用企业协同绑定")
    public RespInfo<ApplicationIntegrationConfig> save(@PathVariable Long appId, @Valid @RequestBody ApplicationIntegrationConfig dto) {
        return RespInfo.success(integrations.save(appId, dto));
    }

    @GetMapping("/capabilities")
    @SaCheckPermission({"ai:businessApplication:list", "ai:capability:query"})
    public RespInfo<Page<AiCapability>> capabilities(@PathVariable Long appId, PageQuery page,
                                                    @RequestParam(required = false) String keyword) {
        return RespInfo.success(integrations.capabilities(appId, page, keyword));
    }

    @PostMapping("/publish/flow")
    @ApiDecrypt
    @Transactional(rollbackFor = Exception.class)
    @SaCheckPermission({"ai:businessApplication:edit", "ai:capability:flow-action:publish"})
    @OperationLog(module = "应用集成", type = OperationType.ADD, desc = "发布应用流程能力")
    public RespInfo<Long> flow(@PathVariable Long appId, @Valid @RequestBody FlowActionCapabilityPublishDTO dto) {
        integrations.requirePublish(appId, dto.getCapabilityCode());
        integrations.requireSource(appId, dto.getSuiteCode(), dto.getObjectCode());
        Long id = flowPublisher.publish(integrations.tenant(), dto);
        integrations.attach(appId, id);
        return RespInfo.success(id);
    }

    @PostMapping("/publish/action")
    @ApiDecrypt
    @Transactional(rollbackFor = Exception.class)
    @SaCheckPermission({"ai:businessApplication:edit", "ai:capability:business-action:publish"})
    @OperationLog(module = "应用集成", type = OperationType.ADD, desc = "发布应用业务动作能力")
    public RespInfo<Long> action(@PathVariable Long appId, @Valid @RequestBody BusinessActionCapabilityPublishDTO dto) {
        integrations.requirePublish(appId, dto.getCapabilityCode());
        integrations.requireSource(appId, dto.getSuiteCode(), dto.getObjectCode());
        Long id = actionPublisher.publish(integrations.tenant(), dto);
        integrations.attach(appId, id);
        return RespInfo.success(id);
    }

    @PostMapping("/publish/system")
    @ApiDecrypt
    @Transactional(rollbackFor = Exception.class)
    @SaCheckPermission({"ai:businessApplication:edit", "ai:capability:system-service:publish"})
    @OperationLog(module = "应用集成", type = OperationType.ADD, desc = "发布应用表单或REST能力")
    public RespInfo<Long> system(@PathVariable Long appId, @Valid @RequestBody SystemServiceCapabilityPublishDTO dto) {
        integrations.requirePublish(appId, dto.capabilityCode());
        if ("lowcode.form.create".equals(dto.serviceCode())) {
            integrations.requireSource(appId, dto.parameters().path("suiteCode").asText(), dto.parameters().path("objectCode").asText());
        } else if (com.mdframe.forge.plugin.capability.secureaction.system.ApplicationProcessStartSystemService.CODE.equals(dto.serviceCode())) {
            if (!appId.toString().equals(dto.parameters().path("applicationId").asText())) {
                throw new BusinessException("业务流程不属于当前应用");
            }
        } else if (!"system.rest.invoke".equals(dto.serviceCode())) {
            throw new BusinessException("应用内仅支持应用表单或显式纳管 REST；审批请选择应用业务流程");
        }
        Long id = systemPublisher.publish(integrations.tenant(), dto);
        integrations.attach(appId, id);
        return RespInfo.success(id);
    }

    @GetMapping("/clients")
    @SaCheckPermission({"ai:businessApplication:list", "ai:capability:grant:query"})
    public RespInfo<List<CapabilityClientVO>> clients(@PathVariable Long appId) {
        integrations.application(appId);
        return RespInfo.success(clients.listGrantOptions(integrations.tenant()));
    }

    @GetMapping("/grants")
    @SaCheckPermission({"ai:businessApplication:list", "ai:capability:grant:query"})
    public RespInfo<Page<AiCapabilityGrant>> grants(@PathVariable Long appId, PageQuery page, @RequestParam Long capabilityId) {
        integrations.requireCapability(appId, capabilityId);
        return RespInfo.success(grants.page(integrations.tenant(), page, null, capabilityId, null));
    }

    @PostMapping("/grants")
    @ApiDecrypt
    @SaCheckPermission({"ai:businessApplication:edit", "ai:capability:grant:add"})
    @OperationLog(module = "应用集成", type = OperationType.ADD, desc = "授权应用能力给接入系统")
    public RespInfo<Long> grant(@PathVariable Long appId, @Valid @RequestBody CapabilityGrantCreateDTO dto) {
        integrations.requireCapability(appId, dto.capabilityId());
        return RespInfo.success(grants.grant(integrations.tenant(), dto));
    }

    @PostMapping("/grants/{grantId}/revoke")
    @SaCheckPermission({"ai:businessApplication:edit", "ai:capability:grant:revoke"})
    @OperationLog(module = "应用集成", type = OperationType.UPDATE, desc = "撤销应用能力授权")
    public RespInfo<Void> revoke(@PathVariable Long appId, @PathVariable Long grantId) {
        AiCapabilityGrant grant = grantMapper.selectTenantById(integrations.tenant(), grantId);
        if (grant == null) throw new BusinessException("授权不存在");
        integrations.requireCapability(appId, grant.getCapabilityId());
        grants.revoke(integrations.tenant(), grantId);
        return RespInfo.success();
    }

    @GetMapping("/invocations")
    @SaCheckPermission({"ai:businessApplication:list", "ai:capability:invocation:query"})
    public RespInfo<Page<CapabilityInvocationDetailVO>> invocations(@PathVariable Long appId, PageQuery page,
            @RequestParam Long capabilityId, @RequestParam(required = false) String requestId) {
        AiCapability capability = integrations.requireCapability(appId, capabilityId);
        return RespInfo.success(audit.page(integrations.tenant(), page, null, requestId,
                capability.getCapabilityCode(), null, null, null));
    }
}
