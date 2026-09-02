package com.mdframe.forge.flow.identity;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mdframe.forge.starter.core.session.SessionHelper;

/**
 * 流程接口只信任当前 Session 身份，并限制分页大小。
 */
public final class FlowSessionIdentity {

    public static final int MAX_PAGE_SIZE = 100;

    private FlowSessionIdentity() {
    }

    public static String requireUserId() {
        Long sessionUserId = SessionHelper.getUserId();
        if (sessionUserId == null || sessionUserId <= 0) {
            throw new IllegalArgumentException("FLOW_TASK_ASSIGNEE_REQUIRED");
        }
        return String.valueOf(sessionUserId);
    }

    public static String requireUserId(String requestedUser) {
        String trusted = requireUserId();
        String requested = requestedUser == null ? null : requestedUser.trim();
        if (requested != null && !requested.isEmpty() && !trusted.equals(requested)) {
            throw new IllegalArgumentException("FLOW_TASK_ASSIGNEE_MISMATCH");
        }
        return trusted;
    }

    public static <T> Page<T> page(Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(num, size);
    }
}
