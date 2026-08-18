package com.mdframe.forge.app.server.bridge;

import com.mdframe.forge.plugin.generator.service.MenuRegisterAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * App 服务只承载低代码运行时，不负责维护 Admin 菜单资源。
 *
 * <p>generator 的发布/配置服务依赖菜单适配器，即使 App 服务只使用运行态接口，
 * 仍需要提供一个无操作实现以完成 Spring 容器装配。真正的菜单注册继续由 Admin 服务负责。</p>
 */
@Slf4j
@Component
public class AppMenuRegisterAdapter implements MenuRegisterAdapter {

    @Override
    public Long registerMenu(String menuName, Long parentId, String configKey, Integer sort) {
        log.debug("[AppMenuRegisterAdapter] Skip menu registration in app server: menuName={}, configKey={}",
                menuName, configKey);
        return null;
    }

    @Override
    public void updateMenu(Long menuResourceId, String menuName, Long parentId, Integer sort) {
        log.debug("[AppMenuRegisterAdapter] Skip menu update in app server: menuId={}, menuName={}",
                menuResourceId, menuName);
    }

    @Override
    public void deleteMenu(Long menuResourceId) {
        log.debug("[AppMenuRegisterAdapter] Skip menu deletion in app server: menuId={}", menuResourceId);
    }
}
