package com.mdframe.forge.plugin.generator.service.lowcode;

import com.mdframe.forge.plugin.generator.domain.entity.AiCrudConfig;
import com.mdframe.forge.plugin.generator.dto.lowcode.LowcodePublishDTO;
import com.mdframe.forge.plugin.generator.service.AiCrudConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 低代码发布事务提交后的异步处理器。
 *
 * <p>负责执行菜单注册/禁用以及业务应用入口同步，这些操作对发布结果不关键，
 * 放在独立事务中异步执行，不影响主发布链路耗时与结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowcodePublishPostProcessor {

    private final LowcodePublishService publishService;
    private final AiCrudConfigService configService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePostPublish(LowcodePublishPostEvent event) {
        AiCrudConfig config = event.config();
        try {
            publishService.registerOrUpdateMenuAsync(config, event.syncMenu(), event.menuParentId());
            publishService.syncBusinessRuntimeEntry(config, event.dto(), event.domainContext());
            configService.updateById(config);
        } catch (Exception e) {
            log.warn("[lowcode-publish] 异步后置处理失败（不影响发布结果）: configKey={}, reason={}",
                    config.getConfigKey(), e.getMessage());
        }
    }
}
