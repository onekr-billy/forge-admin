package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.auth.email.EmailCaptchaSender;
import com.mdframe.forge.starter.core.util.SensitiveDataUtil;
import com.mdframe.forge.starter.message.channel.ChannelType;
import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.message.sdk.MessageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 使用 Forge 消息通道发送邮箱验证码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEmailCaptchaSender implements EmailCaptchaSender {

    private static final String CONTENT_TEMPLATE =
            "您的验证码为${code}，${expireMinutes}分钟内有效，请勿泄露。";

    private final MessageClient messageClient;

    @Override
    public boolean sendVerificationCode(String email, String code, Duration duration) {
        MessageChannel.SendRequest request = new MessageChannel.SendRequest();
        request.setChannel(ChannelType.EMAIL.name());
        request.setType(ChannelType.EMAIL.name());
        request.setTitle("找回密码验证码");
        request.setEmailList(List.of(email));
        request.setContent(CONTENT_TEMPLATE);
        request.setParams(Map.of(
                "code", code,
                "expireMinutes", duration.toMinutes()
        ));
        try {
            MessageChannel.SendResult result = messageClient.send(request);
            return result != null && result.success;
        } catch (RuntimeException exception) {
            log.warn("邮箱验证码通道调用异常: email={}, errorType={}",
                    SensitiveDataUtil.maskEmail(email), exception.getClass().getSimpleName());
            return false;
        }
    }
}
