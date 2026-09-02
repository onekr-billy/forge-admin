package com.mdframe.forge.plugin.system.auth;

import cn.hutool.core.util.StrUtil;
import com.mdframe.forge.starter.core.enums.EnableStatus;
import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.config.EmailConfigProvider;
import com.mdframe.forge.starter.message.config.SmsConfigProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 根据全局短信/邮件配置判断找回密码可用通道。
 */
@Component
@RequiredArgsConstructor
public class RecoveryChannelSupport {

    public static final String CHANNEL_SMS = "sms";
    public static final String CHANNEL_EMAIL = "email";

    private final Optional<SmsConfigProvider> smsConfigProvider;
    private final Optional<EmailConfigProvider> emailConfigProvider;

    public boolean isSmsEnabled() {
        return smsConfigProvider.map(SmsConfigProvider::getSmsConfig)
                .filter(this::smsReady)
                .isPresent();
    }

    public boolean isEmailEnabled() {
        return emailConfigProvider.map(EmailConfigProvider::getEmailConfig)
                .filter(this::emailReady)
                .isPresent();
    }

    public List<String> enabledChannels() {
        List<String> channels = new ArrayList<>();
        if (isSmsEnabled()) {
            channels.add(CHANNEL_SMS);
        }
        if (isEmailEnabled()) {
            channels.add(CHANNEL_EMAIL);
        }
        return channels;
    }

    public void requireChannel(String channel) {
        if (CHANNEL_SMS.equals(channel)) {
            if (!isSmsEnabled()) {
                throw new BusinessException("短信通道未启用，无法通过手机号找回密码");
            }
            return;
        }
        if (CHANNEL_EMAIL.equals(channel)) {
            if (!isEmailEnabled()) {
                throw new BusinessException("邮件通道未启用，无法通过邮箱找回密码");
            }
            return;
        }
        throw new BusinessException("不支持的找回密码方式");
    }

    private boolean smsReady(SmsConfigProvider.SmsConfig config) {
        return config != null
                && EnableStatus.ENABLED.matches(config.getStatus())
                && StrUtil.isNotBlank(config.getAccessKeyId())
                && StrUtil.isNotBlank(config.getTemplateId());
    }

    private boolean emailReady(EmailConfigProvider.EmailConfig config) {
        return config != null
                && EnableStatus.ENABLED.matches(config.getStatus())
                && StrUtil.isNotBlank(config.getSmtpServer())
                && StrUtil.isNotBlank(config.getFromAddress());
    }
}
