package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessBinding;
import com.mdframe.forge.plugin.generator.mapper.BusinessBindingMapper;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves the enabled main flow binding with legacy approval compatibility. */
@Slf4j
final class BusinessFlowBindingResolver {

    private final BusinessBindingMapper bindingMapper;

    BusinessFlowBindingResolver(BusinessBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    AiBusinessBinding selectForStart(Long tenantId, String objectCode, String... fallbackCodes) {
        BindingLookupResult result = lookup(tenantId, objectCode, fallbackCodes);
        if (isEnabled(result.binding())) {
            if ("APPROVAL".equalsIgnoreCase(result.binding().getBindingType())) {
                log.info("[低代码流程启动] 使用历史审批绑定作为主流程: tenantId={}, objectCode={}, binding={}",
                        tenantId, result.matchedObjectCode(), describe(result.binding()));
            }
            return result.binding();
        }
        if (result.binding() != null) {
            log.warn("[低代码流程启动] 未找到启用的主流程绑定: tenantId={}, objectCodes={}, binding={}",
                    tenantId, result.candidates(), describe(result.binding()));
        } else {
            log.warn("[低代码流程启动] 未找到主流程绑定记录: tenantId={}, objectCodes={}",
                    tenantId, result.candidates());
        }
        return null;
    }

    AiBusinessBinding selectForConfig(Long tenantId, String objectCode, String... fallbackCodes) {
        return lookup(tenantId, objectCode, fallbackCodes).binding();
    }

    String describe(AiBusinessBinding binding) {
        if (binding == null) {
            return "null";
        }
        return "id=" + binding.getId()
                + ", type=" + binding.getBindingType()
                + ", status=" + binding.getStatus()
                + ", targetCode=" + binding.getTargetCode()
                + ", bindingKey=" + binding.getBindingKey()
                + ", bindingName=" + binding.getBindingName()
                + ", configBlank=" + StringUtils.isBlank(binding.getBindingConfig());
    }

    private BindingLookupResult lookup(Long tenantId, String objectCode, String... fallbackCodes) {
        List<String> candidates = candidates(objectCode, fallbackCodes);
        AiBusinessBinding firstDisabled = null;
        String disabledObjectCode = null;
        for (String candidate : candidates) {
            AiBusinessBinding binding = bindingMapper.selectBindingByTypeAndCode(
                    tenantId, "OBJECT", candidate, "FLOW");
            if (isEnabled(binding)) {
                return new BindingLookupResult(binding, candidate, candidates);
            }
            if (firstDisabled == null && binding != null) {
                firstDisabled = binding;
                disabledObjectCode = candidate;
            }
            AiBusinessBinding legacy = bindingMapper.selectBindingByTypeAndCode(
                    tenantId, "OBJECT", candidate, "APPROVAL");
            if (isEnabled(legacy)) {
                return new BindingLookupResult(legacy, candidate, candidates);
            }
            if (firstDisabled == null && legacy != null) {
                firstDisabled = legacy;
                disabledObjectCode = candidate;
            }
        }
        return new BindingLookupResult(firstDisabled, disabledObjectCode, candidates);
    }

    private List<String> candidates(String objectCode, String... fallbackCodes) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, objectCode);
        if (fallbackCodes != null) {
            for (String fallbackCode : fallbackCodes) {
                addCandidate(candidates, fallbackCode);
            }
        }
        return new ArrayList<>(candidates);
    }

    private void addCandidate(Set<String> candidates, String objectCode) {
        String normalized = StringUtils.trimToNull(objectCode);
        if (normalized != null) {
            candidates.add(normalized);
        }
    }

    private boolean isEnabled(AiBusinessBinding binding) {
        return binding != null && !EnableStatus.DISABLED.matches(binding.getStatus());
    }

    private record BindingLookupResult(
            AiBusinessBinding binding,
            String matchedObjectCode,
            List<String> candidates) {
    }
}
