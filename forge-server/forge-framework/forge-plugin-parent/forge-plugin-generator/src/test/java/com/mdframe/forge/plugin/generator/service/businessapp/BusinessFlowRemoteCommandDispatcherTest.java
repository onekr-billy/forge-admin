package com.mdframe.forge.plugin.generator.service.businessapp;

import com.mdframe.forge.plugin.generator.domain.entity.AiBusinessFlowRemoteCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessFlowRemoteCommandDispatcherTest {

    @Test
    void routesStartAndTaskCommandsToTheirRecoveryPipelines() {
        BusinessFlowRemoteCommandService commandService = mock(BusinessFlowRemoteCommandService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<BusinessFlowService> provider = mock(ObjectProvider.class);
        BusinessFlowService businessFlowService = mock(BusinessFlowService.class);
        when(provider.getIfAvailable()).thenReturn(businessFlowService);

        AiBusinessFlowRemoteCommand start = command(10L, BusinessFlowRemoteStartEnvelope.COMMAND_TYPE);
        AiBusinessFlowRemoteCommand resubmit = command(
                20L, BusinessFlowRemoteTaskEnvelope.COMMAND_RESUBMIT);
        when(commandService.findRecoveryCandidates(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(start, resubmit));

        new BusinessFlowRemoteCommandDispatcher(commandService, provider).recover();

        verify(businessFlowService).recoverRemoteStartCommand(1L, 10L);
        verify(businessFlowService).recoverRemoteTaskCommand(1L, 20L);
    }

    private AiBusinessFlowRemoteCommand command(Long id, String commandType) {
        AiBusinessFlowRemoteCommand command = new AiBusinessFlowRemoteCommand();
        command.setId(id);
        command.setTenantId(1L);
        command.setCommandType(commandType);
        return command;
    }
}
