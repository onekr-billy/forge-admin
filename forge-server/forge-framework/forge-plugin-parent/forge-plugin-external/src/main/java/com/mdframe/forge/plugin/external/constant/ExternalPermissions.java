package com.mdframe.forge.plugin.external.constant;

/**
 * 外部连接器管理与调用权限。
 */
public final class ExternalPermissions {

    public static final String SYSTEM_QUERY = "external:system:query";
    public static final String SYSTEM_ADD = "external:system:add";
    public static final String SYSTEM_EDIT = "external:system:edit";
    public static final String SYSTEM_REMOVE = "external:system:remove";
    public static final String API_QUERY = "external:api:query";
    public static final String API_ADD = "external:api:add";
    public static final String API_EDIT = "external:api:edit";
    public static final String API_REMOVE = "external:api:remove";
    public static final String PROXY_INVOKE = "external:proxy:invoke";
    public static final String PROXY_DEBUG = "external:proxy:debug";
    public static final String LOG_QUERY = "external:log:query";
    public static final String LOG_REMOVE = "external:log:remove";
    public static final String LOG_CLEAR = "external:log:clear";

    private ExternalPermissions() {
    }
}
