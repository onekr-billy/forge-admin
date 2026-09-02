package com.mdframe.forge.plugin.system.auth;

import com.mdframe.forge.starter.message.channel.MessageChannel;
import com.mdframe.forge.starter.message.sdk.MessageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageEmailCaptchaSenderTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE = "123456";
    private static final Duration DURATION = Duration.ofMinutes(5);

    @Test
    void shouldSendVerificationCodeThroughEmailChannel() {
        MessageClient messageClient = mock(MessageClient.class);
        when(messageClient.send(any())).thenReturn(MessageChannel.SendResult.ok("email-1"));
        MessageEmailCaptchaSender sender = new MessageEmailCaptchaSender(messageClient);

        assertThat(sender.sendVerificationCode(EMAIL, CODE, DURATION)).isTrue();

        ArgumentCaptor<MessageChannel.SendRequest> requestCaptor =
                ArgumentCaptor.forClass(MessageChannel.SendRequest.class);
        verify(messageClient).send(requestCaptor.capture());
        MessageChannel.SendRequest request = requestCaptor.getValue();
        assertThat(request.getChannel()).isEqualTo("EMAIL");
        assertThat(request.getType()).isEqualTo("EMAIL");
        assertThat(request.getEmailList()).containsExactly(EMAIL);
        assertThat(request.getParams())
                .containsEntry("code", CODE)
                .containsEntry("expireMinutes", 5L);
    }

    @Test
    void shouldReturnFalseWhenMessageClientThrows() {
        MessageClient messageClient = mock(MessageClient.class);
        when(messageClient.send(any())).thenThrow(new IllegalStateException("provider unavailable"));
        MessageEmailCaptchaSender sender = new MessageEmailCaptchaSender(messageClient);

        assertThat(sender.sendVerificationCode(EMAIL, CODE, DURATION)).isFalse();
    }
}
