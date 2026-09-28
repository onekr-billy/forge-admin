package com.mdframe.forge.flow.client.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mdframe.forge.flow.client.annotation.FlowBind;
import com.mdframe.forge.flow.client.annotation.FlowCallback;
import com.mdframe.forge.flow.client.annotation.FlowComplete;
import com.mdframe.forge.flow.client.annotation.FlowEventContext;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 流程事件分发器（业务侧）
 * <p>
 * 接收来自 flow-server 的事件消息（Redis 频道消息 or Webhook 请求），
 * 自动路由到当前 Spring 容器中持有 {@link FlowBind} 且 modelKey 匹配的 Bean 的
 * {@link FlowCallback} 方法。
 *
 * <h3>触发方式</h3>
 * <ul>
 *   <li><b>Redis Pub/Sub</b>：由 {@link FlowRedisSubscriber} 监听 {@code flow:event:all} 频道后调用</li>
 *   <li><b>Webhook</b>：由业务方在 Webhook 接收 Controller 中手动调用 {@link #dispatch(FlowEventContext)}</li>
 * </ul>
 *
 * @author forge
 */
@Slf4j
@RequiredArgsConstructor
public class FlowEventSubscriber {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    public FlowEventSubscriber(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * 接收 JSON 消息字符串并分发（Redis 监听器调用此方法）
     */
    public void onMessage(String jsonMessage) {
        try {
            FlowEventContext ctx = objectMapper.readValue(jsonMessage, FlowEventContext.class);
            dispatch(ctx);
        } catch (Exception e) {
            log.warn("[FlowCallback] 消息反序列化失败: {} | raw={}", e.getMessage(), jsonMessage);
        }
    }

    /**
     * 分发事件到匹配的 {@link FlowCallback} 方法
     *
     * @param ctx 流程事件上下文
     */
    public void dispatch(FlowEventContext ctx) {
        if (ctx == null || ctx.getProcessDefKey() == null) {
            return;
        }
        Long tenantId = ctx.getTenantId();
        if (tenantId != null) {
            TenantContextHolder.executeWithTenant(tenantId, () -> doDispatch(ctx));
            return;
        }
        log.warn("[FlowCallback] 流程事件缺少 tenantId，按当前线程租户上下文分发: event={} processDefKey={} businessKey={}",
                ctx.getEvent(), ctx.getProcessDefKey(), ctx.getBusinessKey());
        doDispatch(ctx);
    }

    private void doDispatch(FlowEventContext ctx) {
        boolean matchedBean = false;
        for (FlowBindBean flowBindBean : findFlowBindBeans()) {
            if (!matchesModelKey(flowBindBean.bind().modelKey(), ctx.getProcessDefKey())) {
                continue;
            }
            matchedBean = true;
            invokeCallbacks(flowBindBean.bean(), flowBindBean.targetClass(), ctx);
        }
        if (!matchedBean) {
            log.warn("[FlowCallback] 未找到匹配的 @FlowBind Bean: event={} processDefKey={} businessKey={}",
                    ctx.getEvent(), ctx.getProcessDefKey(), ctx.getBusinessKey());
        }
    }

    private boolean matchesModelKey(String bindModelKey, String processDefKey) {
        return "*".equals(bindModelKey) || bindModelKey.equals(processDefKey);
    }

    private List<FlowBindBean> findFlowBindBeans() {
        List<FlowBindBean> flowBindBeans = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            if (shouldSkipBean(beanName)) {
                continue;
            }
            FlowBindBean flowBindBean = resolveFlowBindBean(beanName);
            if (flowBindBean != null) {
                flowBindBeans.add(flowBindBean);
            }
        }
        return flowBindBeans;
    }

    private boolean shouldSkipBean(String beanName) {
        if (!(applicationContext instanceof ConfigurableApplicationContext configurableApplicationContext)) {
            return false;
        }
        var beanFactory = configurableApplicationContext.getBeanFactory();
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        return beanDefinition.getRole() != BeanDefinition.ROLE_APPLICATION;
    }

    private FlowBindBean resolveFlowBindBean(String beanName) {
        try {
            Object bean = applicationContext.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            FlowBind bind = findFlowBind(targetClass);
            if (bind == null) {
                bind = findFlowBind(applicationContext.getType(beanName));
            }
            if (bind == null) {
                return null;
            }
            return new FlowBindBean(bean, targetClass, bind);
        } catch (BeansException e) {
            log.debug("[FlowCallback] 跳过无法解析的 Bean: beanName={} reason={}", beanName, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.debug("[FlowCallback] 跳过扫描异常的 Bean: beanName={} reason={}", beanName, e.getMessage(), e);
            return null;
        }
    }

    private FlowBind findFlowBind(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return AnnotatedElementUtils.findMergedAnnotation(clazz, FlowBind.class);
    }

    private void invokeCallbacks(Object bean, Class<?> targetClass, FlowEventContext ctx) {
        // 处理 @FlowCallback 注解（方法级别，逐事件匹配）
        for (Method method : targetClass.getDeclaredMethods()) {
            FlowCallback callback = AnnotatedElementUtils.findMergedAnnotation(method, FlowCallback.class);
            if (callback == null) {
                continue;
            }
            boolean matched = Arrays.stream(callback.on()).anyMatch(e -> e.equals(ctx.getEvent()));
            if (!matched) {
                continue;
            }
            invokeMethod(bean, targetClass, method, ctx, "FlowCallback");
        }

        // 处理 @FlowComplete 注解（类级别，按事件类型路由到独立方法）
        FlowComplete complete = AnnotatedElementUtils.findMergedAnnotation(targetClass, FlowComplete.class);
        if (complete != null) {
            String targetMethodName = resolveFlowCompleteMethod(complete, ctx.getEvent());
            if (StringUtils.hasText(targetMethodName)) {
                Method method = findMethod(targetClass, targetMethodName);
                if (method != null) {
                    invokeMethod(bean, targetClass, method, ctx, "FlowComplete");
                } else {
                    log.warn("[FlowComplete] 未找到方法 {}.{} (event={})",
                            targetClass.getSimpleName(), targetMethodName, ctx.getEvent());
                }
            }
        }
    }

    private String resolveFlowCompleteMethod(FlowComplete complete, String event) {
        if (FlowCallback.ON_COMPLETED.equals(event)) return complete.onApproved();
        if (FlowCallback.ON_REJECTED.equals(event))  return complete.onRejected();
        if (FlowCallback.ON_CANCELED.equals(event))  return complete.onCanceled();
        return null;
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)
                    && (m.getParameterCount() == 0
                        || (m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(FlowEventContext.class)))) {
                return m;
            }
        }
        return null;
    }

    /** 调用目标方法，支持无参或 (FlowEventContext) 两种签名 */
    private void invokeMethod(Object bean, Class<?> targetClass, Method method,
                              FlowEventContext ctx, String annotationName) {
        try {
            Object invocationTarget = resolveInvocationTarget(bean, targetClass, method, annotationName);
            method.setAccessible(true);
            if (method.getParameterCount() == 0) {
                method.invoke(invocationTarget);
            } else if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(FlowEventContext.class)) {
                method.invoke(invocationTarget, ctx);
            } else {
                log.warn("[{}] 方法签名不符合规范（须无参或接收 FlowEventContext）: {}.{}",
                        annotationName, targetClass.getSimpleName(), method.getName());
                return;
            }
            log.debug("[{}] 回调成功 {}.{} event={} businessKey={}",
                    annotationName, targetClass.getSimpleName(), method.getName(),
                    ctx.getEvent(), ctx.getBusinessKey());
        } catch (Exception e) {
            Throwable root = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            log.error("[{}] 回调执行失败 {}.{}: {}",
                    annotationName, targetClass.getSimpleName(), method.getName(), root.getMessage(), root);
        }
    }

    private Object resolveInvocationTarget(Object bean, Class<?> targetClass, Method method, String annotationName) {
        if (method.getDeclaringClass().isInstance(bean)) {
            return bean;
        }
        if (bean instanceof Advised advised) {
            try {
                Object target = advised.getTargetSource().getTarget();
                if (target != null && method.getDeclaringClass().isInstance(target)) {
                    return target;
                }
            } catch (Exception e) {
                log.warn("[{}] 获取代理目标对象失败 {}.{}: {}",
                        annotationName, targetClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
        return bean;
    }

    private record FlowBindBean(Object bean, Class<?> targetClass, FlowBind bind) {
    }
}
