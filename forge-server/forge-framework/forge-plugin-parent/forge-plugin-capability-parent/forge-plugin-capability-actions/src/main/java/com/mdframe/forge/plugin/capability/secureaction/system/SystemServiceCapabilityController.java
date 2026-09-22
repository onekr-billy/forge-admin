package com.mdframe.forge.plugin.capability.secureaction.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mdframe.forge.starter.core.annotation.crypto.ApiDecrypt;
import com.mdframe.forge.starter.core.annotation.log.OperationLog;
import com.mdframe.forge.starter.core.domain.OperationType;
import com.mdframe.forge.starter.core.domain.RespInfo;
import com.mdframe.forge.starter.core.session.SessionHelper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/capability/system-service")
public class SystemServiceCapabilityController {

    private final SystemServiceCapabilityPublisher publisher;

    public SystemServiceCapabilityController(SystemServiceCapabilityPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping("/registration-source")
    @SaCheckPermission("ai:capability:system-service:publish")
    @OperationLog(module = "AI中枢能力", type = OperationType.QUERY, desc = "查询系统服务注册来源")
    public RespInfo<List<SystemServiceRegistrationSource>> registrationSources(
            @RequestParam(required = false) String serviceCode) {
        return RespInfo.success(publisher.registrationSources(SessionHelper.getTenantId(), serviceCode));
    }

    @PostMapping("/publish")
    @SaCheckPermission("ai:capability:system-service:publish")
    @OperationLog(module = "AI中枢能力", type = OperationType.ADD, desc = "发布受控系统服务能力")
    @ApiDecrypt
    public RespInfo<Long> publish(@Valid @RequestBody SystemServiceCapabilityPublishDTO dto) {
        return RespInfo.success(publisher.publish(SessionHelper.getTenantId(), dto));
    }
}
