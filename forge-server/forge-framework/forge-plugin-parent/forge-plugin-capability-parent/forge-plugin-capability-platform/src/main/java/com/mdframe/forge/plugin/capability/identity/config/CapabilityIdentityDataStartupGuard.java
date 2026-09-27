package com.mdframe.forge.plugin.capability.identity.config;

import com.mdframe.forge.plugin.capability.identity.mapper.CapabilityIdentityStartupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * 开放身份能力启动前的数据门禁。
 *
 * <p>只有 identity 或 open-gateway 显式开启时才由自动配置注册。遗留的占位客户端、
 * 缺失 SERVICE 租户/组织绑定以及不完整授权会直接阻止启动，避免配置开关打开后把
 * 历史草稿数据暴露为可认证入口。</p>
 */
@RequiredArgsConstructor
@Slf4j
public final class CapabilityIdentityDataStartupGuard implements ApplicationRunner {

    static final List<String> RESERVED_CLIENT_CODES = List.of(
            "default", "demo", "sample", "test", "example", "changeme", "change_me");

    private final CapabilityIdentityStartupMapper startupMapper;

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    void verify() {
        int unsafeClients = startupMapper.countUnsafeEnabledClients(RESERVED_CLIENT_CODES);
        int unsafeGrants = startupMapper.countUnsafeEnabledGrants(RESERVED_CLIENT_CODES);
        if (unsafeClients > 0 || unsafeGrants > 0) {
            throw new IllegalStateException(
                    "Forge Capability 身份启动检查失败：存在 " + unsafeClients
                            + " 个不安全客户端和 " + unsafeGrants
                            + " 个不安全授权；请先吊销或修正后再启用开放网关");
        }
        log.info("Forge Capability 身份数据启动检查通过：未发现占位客户端、无效 SERVICE 绑定或不安全授权");
    }
}
