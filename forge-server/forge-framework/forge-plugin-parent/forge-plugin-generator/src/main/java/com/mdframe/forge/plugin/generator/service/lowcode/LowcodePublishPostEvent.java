package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;

/**
 * 低代码发布事务提交后触发的异步事件。
 *
 * <p>主事务只负责核心数据写入（config + version），菜单注册与业务入口同步
 * 放到事务提交后异步执行，从而显著缩短发布接口耗时。</p>
 */
public record LowcodePublishPostEvent(
        AiCrudConfig config,
        LowcodePublishDTO dto,
        LowcodePublishService.PublishDomainContext domainContext,
        boolean syncMenu,
        Long menuParentId
) {
}
