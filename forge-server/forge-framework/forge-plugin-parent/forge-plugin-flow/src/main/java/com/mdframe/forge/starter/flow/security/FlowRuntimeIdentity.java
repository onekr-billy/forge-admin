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

    private static Long requireTenantId(LoginUser loginUser) {
        Long tenantId = loginUser.getTenantId();
        if (tenantId == null || tenantId <= 0) {
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
        LoginUser loginUser;
        try {
            loginUser = SessionHelper.getLoginUser();
        } catch (Exception exception) {
            loginUser = null;
        }
        if (loginUser == null) {
            throw new IllegalStateException("FLOW_IDENTITY_REQUIRED");
        }
        return loginUser;
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
