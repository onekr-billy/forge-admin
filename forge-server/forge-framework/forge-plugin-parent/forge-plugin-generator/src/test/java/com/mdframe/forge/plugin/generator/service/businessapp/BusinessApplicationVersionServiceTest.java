package com.mdframe.forge.plugin.generator.service.businessapp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mdframe.forge.plugin.generator.constant.BusinessApplicationPublishStatus;
import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessApplicationVersion;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationMapper;
import com.mdframe.forge.plugin.generator.mapper.BusinessApplicationVersionMapper;
import com.mdframe.forge.plugin.generator.service.printing.PrintApplicationVersionGuard;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("BusinessApplicationVersionService")
class BusinessApplicationVersionServiceTest {

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
                "application_version_test", "token-app-version", Set.of()));
    }

    @AfterEach
    void clearIdentity() {
        if (identityScope != null) {
            identityScope.close();
        }
        ExecutionIdentityContextHolder.clear();
    }

    @Test
    @DisplayName("幂等提交已有版本时仍将应用状态收敛为已发布")
    void existingVersionStillMarksApplicationPublished() throws Exception {
        AiBusinessApplicationVersion existing = new AiBusinessApplicationVersion();
        existing.setId(99L);
        existing.setApplicationId(10L);
        existing.setVersionNo(3);
        existing.setSnapshotHash("same-hash");
        existing.setPublishedTime(LocalDateTime.now().minusMinutes(5));
        BusinessApplicationVersionMapper versionMapper = proxy(BusinessApplicationVersionMapper.class,
                (method, args) -> "selectVersion".equals(method) ? existing : defaultValue(method));
        AtomicInteger markedVersion = new AtomicInteger();
        BusinessApplicationMapper applicationMapper = proxy(BusinessApplicationMapper.class, (method, args) -> {
            if ("markPublished".equals(method)) {
                markedVersion.set((Integer) args[2]);
                return 1;
            }
            return defaultValue(method);
        });
        BusinessApplicationVersionService service = new BusinessApplicationVersionService(
                null, applicationMapper, null, org.mockito.Mockito.mock(
                        com.mdframe.forge.plugin.generator.service.printing.PrintApplicationVersionGuard.class));
        setBaseMapper(service, versionMapper);
        BusinessApplicationSnapshotService.SnapshotBundle snapshot
                = new BusinessApplicationSnapshotService.SnapshotBundle("{}", "same-hash", Map.of());

        AiBusinessApplicationVersion result = service.commitImmutable(
                10L, 3, snapshot, BusinessApplicationPublishStatus.PUBLISHED.getCode(), null, "重试发布");

        assertEquals(99L, result.getId());
        assertEquals(3, markedVersion.get());
    }

    @Test
    @DisplayName("缺少可信身份时在版本锁与数据库读取前拒绝提交")
    void missingIdentityRejectsCommitBeforeSideEffects() throws Exception {
        identityScope.close();
        identityScope = null;
        BusinessApplicationVersionMapper versionMapper = mock(BusinessApplicationVersionMapper.class);
        BusinessApplicationMapper applicationMapper = mock(BusinessApplicationMapper.class);
        PrintApplicationVersionGuard guard = mock(PrintApplicationVersionGuard.class);
        BusinessApplicationVersionService service = new BusinessApplicationVersionService(
                null, applicationMapper, null, guard);
        setBaseMapper(service, versionMapper);
        BusinessApplicationSnapshotService.SnapshotBundle snapshot
                = new BusinessApplicationSnapshotService.SnapshotBundle("{}", "same-hash", Map.of());

        BusinessException error = assertThrows(BusinessException.class, () -> service.commitImmutable(
                10L, 3, snapshot, BusinessApplicationPublishStatus.PUBLISHED.getCode(), null, "发布"));

        assertEquals("应用版本操作缺少可信租户上下文", error.getMessage());
        verifyNoInteractions(versionMapper, applicationMapper, guard);
    }

    @Test
    @DisplayName("缺少租户时在应用详情读取前拒绝版本查询")
    void missingTenantRejectsDetailBeforeApplicationLookup() throws Exception {
        identityScope.close();
        identityScope = null;
        BusinessApplicationService applicationService = mock(BusinessApplicationService.class);
        BusinessApplicationVersionMapper versionMapper = mock(BusinessApplicationVersionMapper.class);
        BusinessApplicationVersionService service = new BusinessApplicationVersionService(
                applicationService, mock(BusinessApplicationMapper.class), null,
                mock(PrintApplicationVersionGuard.class));
        setBaseMapper(service, versionMapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.detail(10L, 3));

        assertEquals("应用版本操作缺少可信租户上下文", error.getMessage());
        verifyNoInteractions(applicationService, versionMapper);
    }

    private static void setBaseMapper(Object service, Object mapper) throws Exception {
        Field field = ServiceImpl.class.getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return handler.invoke(method.getName(), args == null ? new Object[0] : args);
                });
    }

    private static Object defaultValue(String method) {
        return switch (method) {
            case "insert", "markPublished" -> 1;
            default -> null;
        };
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
