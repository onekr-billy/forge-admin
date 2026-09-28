package com.mdframe.forge.starter.flow.security;

import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.springframework.util.StringUtils;

/**
 * Flow 运行入口的可信会话身份快照。
 *
 * <p>Flow Controller 为了使用显式租户 SQL 会进入 {@code @IgnoreTenant}
 * 作用域，因此这里以已验证的登录会话为权威来源；若线程同时
 * 存在显式租户上下文，则两者必须一致。</p>
 */
public final class FlowRuntimeIdentity {

    private FlowRuntimeIdentity() {
    }

    public static Long requireTenantId() {
        LoginUser loginUser = requireLoginUser();
        return requireTenantId(loginUser);
    }

    /**
     * 获取会话或已隔离后台任务建立的可信租户。
     *
     * <p>有登录会话时以会话租户为准，并校验线程租户一致性；无会话时仅接受
     * 非 {@code ignoreTenant} 作用域中显式设置的正数租户。这样既支持后台任务，
     * 又不会把全租户扫描作用域误当成某个业务租户。</p>
     */
    public static Long requireTenantIdFromSessionOrScope() {
        LoginUser loginUser = currentLoginUser();
        Long sessionTenantId = loginUser == null ? null : loginUser.getTenantId();
        Long scopedTenantId = TenantContextHolder.getTenantId();
        if (loginUser != null) {
            if (!isPositive(sessionTenantId)) {
                throw new IllegalStateException("FLOW_TENANT_REQUIRED");
            }
            if (scopedTenantId != null && !sessionTenantId.equals(scopedTenantId)) {
                throw new IllegalStateException("FLOW_TENANT_MISMATCH");
            }
            return sessionTenantId;
        }
        if (!TenantContextHolder.isIgnore() && isPositive(scopedTenantId)) {
            return scopedTenantId;
        }
        throw new IllegalStateException("FLOW_TENANT_REQUIRED");
    }

    private static Long requireTenantId(LoginUser loginUser) {
        Long tenantId = loginUser.getTenantId();
        if (!isPositive(tenantId)) {
            throw new IllegalStateException("FLOW_TENANT_REQUIRED");
        }
        Long scopedTenantId = TenantContextHolder.getTenantId();
        if (scopedTenantId != null && !tenantId.equals(scopedTenantId)) {
            throw new IllegalStateException("FLOW_TENANT_MISMATCH");
        }
        return tenantId;
    }

    public static Actor requireActor() {
        LoginUser loginUser = requireLoginUser();
        Long tenantId = requireTenantId(loginUser);
        Long userId = loginUser.getUserId();
        if (userId == null || userId <= 0) {
            throw new IllegalStateException("FLOW_USER_REQUIRED");
        }
        return new Actor(
                tenantId,
                userId,
                firstText(loginUser.getRealName(), loginUser.getUsername()),
                loginUser.getMainOrgId(),
                loginUser.getDeptName());
    }

    private static LoginUser requireLoginUser() {
        LoginUser loginUser = currentLoginUser();
        if (loginUser == null) {
            throw new IllegalStateException("FLOW_IDENTITY_REQUIRED");
        }
        return loginUser;
    }

    private static LoginUser currentLoginUser() {
        try {
            return SessionHelper.getLoginUser();
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    public record Actor(Long tenantId, Long userId, String userName, Long deptId, String deptName) {
    }
}
