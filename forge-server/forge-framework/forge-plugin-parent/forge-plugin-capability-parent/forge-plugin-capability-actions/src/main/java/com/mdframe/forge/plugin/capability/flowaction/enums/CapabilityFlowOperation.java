package com.mdframe.forge.plugin.capability.flowaction.enums;

import java.util.Arrays;

/** 对外流程操作；存储码与 ai_capability_flow_operation 字典一致。 */
public enum CapabilityFlowOperation {
    SUBMIT, START, APPROVE, REJECT, WITHDRAW;

    public String getCode() { return name(); }

    public boolean matches(String code) { return name().equalsIgnoreCase(code); }

    public static boolean supports(String code) {
        return Arrays.stream(values()).anyMatch(value -> value.matches(code));
    }
}
