package com.mdframe.forge.starter.core.annotation.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明 REST 方法可在可信能力身份上下文内调用。
 * 方法必须使用 SessionHelper 获取身份，保留 Service 的数据权限与业务校验。
 * 不能依赖 Servlet Request、原始 Token、HTTP 拦截器或任意 Header 建立授权。
 * 注册并不等于开放：仍须发布能力版本、授权客户端及满足声明的业务权限。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenRestCapability {
    String name();
    String permission();
    String description() default "";
    String version() default "1";
}
