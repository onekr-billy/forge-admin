package com.mdframe.forge.plugin.message.service.impl;

import com.mdframe.forge.plugin.message.domain.MessageSendStatus;
import com.mdframe.forge.plugin.message.mapper.SysMessageMapper;
import com.mdframe.forge.plugin.message.mapper.SysMessageReceiverMapper;
import com.mdframe.forge.plugin.message.mapper.SysMessageSendRecordMapper;
import com.mdframe.forge.plugin.message.mapper.SysMessageTemplateMapper;
import com.mdframe.forge.plugin.message.service.MessageReceiverResolver;
import com.mdframe.forge.plugin.message.service.SysMessageReceiverService;
import com.mdframe.forge.plugin.system.service.ISysUserService;
import com.mdframe.forge.starter.message.sdk.MessageClient;
import com.mdframe.forge.starter.message.service.MessageTemplateEngine;
import com.mdframe.forge.starter.tenant.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class MessageServiceImplTest {

    private SysMessageReceiverMapper receiverMapper;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        receiverMapper = mock(SysMessageReceiverMapper.class);
        messageService = new MessageServiceImpl(
                mock(SysMessageMapper.class),
                receiverMapper,
                mock(SysMessageSendRecordMapper.class),
                mock(SysMessageTemplateMapper.class),
                mock(MessageClient.class),
                mock(MessageTemplateEngine.class),
                mock(MessageReceiverResolver.class),
                mock(SysMessageReceiverService.class),
                mock(ISysUserService.class),
                mock(ApplicationEventPublisher.class));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExposePartialStatusWhenSomeRecipientsFail() {
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 2, 1, 0)).isEqualTo(MessageSendStatus.PARTIAL);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 2, 0, 1)).isEqualTo(MessageSendStatus.PARTIAL);
    }

    @Test
    void shouldKeepSuccessAndCompleteFailureStatuses() {
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 2, 0, 0)).isEqualTo(MessageSendStatus.SUCCESS);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 0, 2, 0)).isEqualTo(MessageSendStatus.FAILED);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(2, 0, 0, 2)).isEqualTo(MessageSendStatus.FAILED);
        assertThat(MessageServiceImpl.resolveCollaborationStatus(3, 0, 1, 2)).isEqualTo(MessageSendStatus.FAILED);
    }

    @Test
    void shouldMarkAllMessagesReadWithSingleBatchUpdate() {
        messageService.markAllRead(42L);

        verify(receiverMapper).markAllMessagesRead(eq(1L), eq(42L), any(LocalDateTime.class));
        verifyNoMoreInteractions(receiverMapper);
    }

    @Test
    void shouldMarkSelectedMessagesReadWithSingleBatchUpdate() {
        List<Long> messageIds = List.of(11L, 12L);

        messageService.markReadBatch(messageIds, 42L);

        verify(receiverMapper).markMessagesReadBatch(
                eq(1L), eq(42L), eq(messageIds), any(LocalDateTime.class));
        verifyNoMoreInteractions(receiverMapper);
    }

    @Test
    void shouldSkipBatchUpdateWhenMessageIdsAreEmpty() {
        messageService.markReadBatch(List.of(), 42L);

        verifyNoInteractions(receiverMapper);
    }
}
