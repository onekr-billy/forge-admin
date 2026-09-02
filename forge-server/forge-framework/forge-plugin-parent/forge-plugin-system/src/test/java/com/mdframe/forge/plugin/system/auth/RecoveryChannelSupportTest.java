package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.core.exception.BusinessException;
import com.mdframe.forge.starter.message.config.EmailConfigProvider;
import com.mdframe.forge.starter.message.config.SmsConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryChannelSupportTest {

    @Test
    void shouldDisableChannelsWhenGlobalConfigMissing() {
        RecoveryChannelSupport support = new RecoveryChannelSupport(Optional.empty(), Optional.empty());

        assertThat(support.isSmsEnabled()).isFalse();
        assertThat(support.isEmailEnabled()).isFalse();
        assertThat(support.enabledChannels()).isEmpty();
        assertThatThrownBy(() -> support.requireChannel("sms"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("短信通道未启用，无法通过手机号找回密码");
    }

    @Test
    void shouldEnableSmsOnlyWhenStatusAndRequiredFieldsReady() {
        SmsConfigProvider.SmsConfig config = new SmsConfigProvider.SmsConfig();
        config.setStatus(1);
        config.setAccessKeyId("ak");
        config.setTemplateId("tpl");
        RecoveryChannelSupport support = new RecoveryChannelSupport(
                Optional.of(() -> config), Optional.empty());

        assertThat(support.enabledChannels()).containsExactly("sms");
        support.requireChannel("sms");
        assertThatThrownBy(() -> support.requireChannel("email"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("邮件通道未启用，无法通过邮箱找回密码");
    }

    @Test
    void shouldIgnoreDisabledOrIncompleteEmailConfig() {
        EmailConfigProvider.EmailConfig disabled = new EmailConfigProvider.EmailConfig();
        disabled.setStatus(0);
        disabled.setSmtpServer("smtp.example.com");
        disabled.setFromAddress("noreply@example.com");
        RecoveryChannelSupport disabledSupport = new RecoveryChannelSupport(
                Optional.empty(), Optional.of(() -> disabled));
        assertThat(disabledSupport.isEmailEnabled()).isFalse();

        EmailConfigProvider.EmailConfig incomplete = new EmailConfigProvider.EmailConfig();
        incomplete.setStatus(1);
        incomplete.setSmtpServer("smtp.example.com");
        RecoveryChannelSupport incompleteSupport = new RecoveryChannelSupport(
                Optional.empty(), Optional.of(() -> incomplete));
        assertThat(incompleteSupport.isEmailEnabled()).isFalse();

        EmailConfigProvider.EmailConfig ready = new EmailConfigProvider.EmailConfig();
        ready.setStatus(1);
        ready.setSmtpServer("smtp.example.com");
        ready.setFromAddress("noreply@example.com");
        RecoveryChannelSupport readySupport = new RecoveryChannelSupport(
                Optional.empty(), Optional.of(() -> ready));
        assertThat(readySupport.enabledChannels()).containsExactly("email");
    }
}
