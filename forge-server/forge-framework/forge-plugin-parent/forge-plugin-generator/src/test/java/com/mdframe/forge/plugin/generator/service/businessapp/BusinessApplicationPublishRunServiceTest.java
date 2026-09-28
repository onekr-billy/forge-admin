package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStatus;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStep;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationPublishRun;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationPublishRunMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationVersionMapper;
import com.mdframe.forge.plugin.generator.vo.businessapp.BusinessApplicationAssetSelectionVO;
import com.mdframe.forge.starter.core.context.ExecutionIdentity;
import com.mdframe.forge.starter.core.context.ExecutionIdentityContextHolder;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.core.session.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BusinessApplicationPublishRunService")
class BusinessApplicationPublishRunServiceTest {

    private ExecutionIdentityContextHolder.Scope identityScope;

    @BeforeEach
    void setUpIdentity() {
        LoginUser user = new LoginUser();
        user.setTenantId(1L);
        user.setUserId(101L);
        user.setUsername("publisher");
        user.setActiveOrgId(201L);
        identityScope = ExecutionIdentityContextHolder.open(new ExecutionIdentity(
                user, "USER", 101L, null, 301L,
                "publish_run_test", "token-publish-run", Set.of()));
    }

    @AfterEach
    void clearIdentity() {
        if (identityScope != null) {
            identityScope.close();
        }
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("page menu synchronization is a named side-effect step")
    void pageMenusIsNamedSideEffectStep() throws Exception {
        AtomicReference<AiBusinessApplicationPublishRun> inserted = new AtomicReference<>();
        AtomicReference<String> progressStatus = new AtomicReference<>();
        BusinessApplicationPublishRunMapper runMapper = proxy(
                BusinessApplicationPublishRunMapper.class, (method, args) -> switch (method) {
                    case "lockApplication" -> 10L;
                    case "selectMaxTargetVersionNo" -> 0;
                    case "insert" -> {
                        inserted.set((AiBusinessApplicationPublishRun) args[0]);
                        yield 1;
                    }
                    case "updateProgress" -> {
                        progressStatus.set(String.valueOf(args[3]));
                        yield 1;
                    }
                    default -> null;
                });
        BusinessApplicationVersionMapper versionMapper = proxy(
                BusinessApplicationVersionMapper.class,
                (method, args) -> "selectMaxVersionNo".equals(method) ? 0 : null);
        BusinessApplicationPublishRunService service = new BusinessApplicationPublishRunService(
                new ObjectMapper().findAndRegisterModules(), versionMapper);
        injectMapper(service, runMapper);

        AiBusinessApplicationPublishRun run = service.reserve(
                10L,
                "publish-run-test-0001",
                "PUBLISH",
                null,
                new BusinessApplicationSnapshotService.SnapshotBundle("{}", "hash", Map.of()),
                new BusinessApplicationAssetSelectionVO());

        assertNotNull(inserted.get());
        assertEquals("同步应用页面菜单", service.toVO(run).getSteps().stream()
                .filter(step -> BusinessApplicationPublishStep.PAGE_MENUS.equals(step.getStepCode()))
                .findFirst().orElseThrow().getStepName());

        run.setId(20L);
        service.markFailed(run, BusinessApplicationPublishStep.PAGE_MENUS,
                "PAGE_MENU_FAILED", "页面菜单同步失败");

        assertEquals(BusinessApplicationPublishStatus.PARTIAL.getCode(), progressStatus.get());
        assertEquals(BusinessApplicationPublishStatus.PARTIAL.getCode(), run.getRunStatus());
    }

    @Test
    @DisplayName("missing trusted identity is rejected before reserving a version")
    void missingIdentityIsRejectedBeforePersistence() throws Exception {
        identityScope.close();
        identityScope = null;
        AtomicReference<String> invokedMethod = new AtomicReference<>();
        BusinessApplicationPublishRunMapper runMapper = proxy(
                BusinessApplicationPublishRunMapper.class, (method, args) -> {
                    invokedMethod.set(method);
                    return null;
                });
        BusinessApplicationPublishRunService service = new BusinessApplicationPublishRunService(
                new ObjectMapper().findAndRegisterModules(), proxy(
                        BusinessApplicationVersionMapper.class, (method, args) -> null));
        injectMapper(service, runMapper);

        BusinessException error = assertThrows(BusinessException.class, () -> service.reserve(
                10L,
                "publish-run-test-0002",
                "PUBLISH",
                null,
                new BusinessApplicationSnapshotService.SnapshotBundle("{}", "hash", Map.of()),
                new BusinessApplicationAssetSelectionVO()));

        assertEquals("应用发布运行单缺少可信租户上下文", error.getMessage());
        assertNull(invokedMethod.get());
    }

    private static void injectMapper(BusinessApplicationPublishRunService service,
                                     BusinessApplicationPublishRunMapper mapper) throws Exception {
        Field field = ServiceImpl.class.getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
