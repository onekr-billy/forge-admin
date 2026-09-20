package com.mdframe.forge.plugin.generator.service.audit;

import com.mdframe.forge.plugin.generator.enums.DataAuditActorType;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.session.LoginUser;
import com.mdframe.forge.starter.core.session.SessionHelper;
import org.apache.commons.lang3.StringUtils;

public final class DataAuditActorResolver {

    private DataAuditActorResolver() {
    }

    public static Actor current() {
        ExecutionIdentity identity = ExecutionIdentityContextHolder.current().orElse(null);
        if (identity != null) {
            LoginUser user = identity.loginUser();
            return new Actor(
                    DataAuditActorType.fromCode(identity.actorType()),
                    stringify(identity.actorUserId()),
                    displayName(user),
                    stringify(identity.clientId()),
                    stringify(identity.serviceUserId())
            );
        }
        LoginUser user = null;
        try {
            user = SessionHelper.getLoginUser();
        } catch (Exception ignored) {
            // 无登录会话时按系统主体记录
        }
        if (user == null || user.getUserId() == null) {
            return new Actor(DataAuditActorType.SYSTEM, "system", "系统", null, null);
        }
        return new Actor(
                DataAuditActorType.USER,
                stringify(user.getUserId()),
                displayName(user),
                null,
                null
        );
    }

    private static String displayName(LoginUser user) {
        if (user == null) {
            return "系统";
        }
        if (StringUtils.isNotBlank(user.getRealName())) {
            return user.getRealName();
        }
        return StringUtils.defaultIfBlank(user.getUsername(), stringify(user.getUserId()));
    }

    private static String stringify(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    public record Actor(DataAuditActorType type,
                        String actorId,
                        String actorName,
                        String clientId,
                        String delegatedUserId) {
    }
}
