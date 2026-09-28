package com.mdframe.forge.plugin.system.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 授权树与权限加载对 visible 的处理口径契约：
 * visible/menu_status 只控制导航显隐，权限链路不得一刀切；
 * 授权树仅剔除「双隐藏」（visible=0 且 menu_status=0）的彻底停用资源。
 */
class SysResourceAssignableTreeContractTest {

    private static final Path SERVICE_IMPL = Path.of(
            "src/main/java/com/mdframe/forge/plugin/system/service/impl/SysResourceServiceImpl.java");
    private static final Path USER_LOAD_IMPL = Path.of(
            "src/main/java/com/mdframe/forge/plugin/system/service/impl/UserLoadServiceImpl.java");

    @Test
    void assignableTreeMustExcludeFullyDisabledResourcesButKeepHiddenPages() throws IOException {
        String source = Files.readString(SERVICE_IMPL);
        String assignable = method(source, "selectAssignableResourceTree");
        String menuTree = method(source, "selectResourceTree");

        // 授权树必须剔除 visible=0 且 menu_status=0 的彻底停用资源
        assertThat(assignable)
                .contains("wrapper.and(w -> w.ne(SysResource::getVisible, 0).or().ne(SysResource::getMenuStatus, 0))");

        // 菜单管理树必须能看到隐藏资源（供管理员维护），禁止加同款过滤
        assertThat(menuTree)
                .doesNotContain("SysResource::getVisible, 0")
                .doesNotContain("SysResource::getMenuStatus, 0");
    }

    @Test
    void permissionLoadingMustNotFilterByVisible() throws IOException {
        String source = Files.readString(USER_LOAD_IMPL);

        // 历史坑防回归：权限加载（perms 与 apiPermissions）都不得按 visible=1 过滤，
        // 否则角色已绑定的隐藏菜单/隐藏 API 会被鉴权层拦成 403
        assertThat(source)
                .doesNotContain(".eq(SysResource::getVisible, 1)");
    }

    private String method(String source, String name) {
        int start = source.indexOf("public List<SysResource> " + name + "(");
        assertThat(start).as("missing method %s", name).isGreaterThanOrEqualTo(0);
        int end = source.indexOf("\n    }", start);
        assertThat(end).as("unclosed method %s", name).isGreaterThan(start);
        return source.substring(start, end);
    }
}
