package com.mdframe.forge.starter.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 验证码配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "forge.captcha")
public class CaptchaProperties {

    /** 单一手机号/邮箱每天最多发送次数。 */
    private int dailyTargetLimit = 10;

    /** 单一来源 IP 每天最多发送次数。 */
    private int dailyIpLimit = 50;

    /** 单一设备指纹每天最多发送次数。 */
    private int dailyDeviceLimit = 30;

    /** 单一租户每天最多发送次数；匿名流量归入 anonymous 桶。 */
    private int dailyTenantLimit = 500;

    /** 同一短信/邮箱目标在失败窗口内允许的最大验证码错误次数。 */
    private int verificationMaxFailures = 5;

    /** 验证码错误次数统计窗口。 */
    private Duration verificationFailureWindow = Duration.ofMinutes(10);

    /** 达到失败阈值后的短时锁定时长。 */
    private Duration verificationLockDuration = Duration.ofMinutes(10);

    /**
     * 是否在接口响应中回显验证码明文（dev-echo）
     * 默认关闭；只有 dev/local Profile 且显式开启时，图形/短信验证码才会随响应返回 code 字段，
     * 同时短信走本地模拟发送。其他 Profile 即使误配置为 true 也不会生效
     */
    private boolean devEchoCode = false;

    /**
     * 滑块验证码挑战签名密钥。生产环境必须通过 Secret 注入，禁止写入配置文件。
     */
    private String challengeSecret = "";
}
