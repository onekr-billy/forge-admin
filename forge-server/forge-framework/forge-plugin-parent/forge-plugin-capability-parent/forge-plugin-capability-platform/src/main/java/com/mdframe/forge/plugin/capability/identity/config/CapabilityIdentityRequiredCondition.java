package com.mdframe.forge.plugin.capability.identity.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

/**
 * 能力身份运行底座的统一启用条件。
 *
 * <p>开放网关依赖 Token 签发与校验能力，因此只要开放网关开启，也必须注册
 * OAuth 路由和身份运行组件，避免出现服务已装配、公开 Token 路由却返回 404
 * 的半开启状态。</p>
 */
public final class CapabilityIdentityRequiredCondition extends AnyNestedCondition {

    public CapabilityIdentityRequiredCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
            prefix = "forge.capability.identity",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class IdentityEnabled {
    }

    @ConditionalOnProperty(
            prefix = "forge.capability.open-gateway",
            name = "enabled",
            havingValue = "true")
    static class OpenGatewayEnabled {
    }
}
