package com.mdframe.forge.starter.flow.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowBusinessAndModelStatusTest {

    @Test
    void shouldKeepStableBusinessStatusCodes() {
        assertThat(FlowBusinessStatus.RUNNING.getCode()).isEqualTo("running");
        assertThat(FlowBusinessStatus.isEnded("terminated")).isTrue();
        assertThat(FlowBusinessStatus.isEnded("running")).isFalse();
        assertThat(FlowBusinessStatus.isReusable("draft")).isTrue();
        assertThat(FlowBusinessStatus.isPending("active")).isTrue();
    }

    @Test
    void shouldKeepStableModelStatusCodes() {
        assertThat(FlowModelStatus.DESIGNING.getCode()).isZero();
        assertThat(FlowModelStatus.PUBLISHED.getCode()).isEqualTo(1);
        assertThat(FlowModelStatus.SUSPENDED.getCode()).isEqualTo(2);
        assertThat(FlowModelStatus.DISABLED.getCode()).isEqualTo(3);
        assertThat(FlowModelStatus.PUBLISHED.matches(1)).isTrue();
        assertThat(FlowModelStatus.of(2)).isEqualTo(FlowModelStatus.SUSPENDED);
    }

    @Test
    void shouldKeepStableErrorLogAndFormStatuses() {
        assertThat(FlowErrorLogStatus.UNRESOLVED.getCode()).isZero();
        assertThat(FlowErrorLogStatus.RETRIED.getCode()).isEqualTo(1);
        assertThat(FlowErrorLogStatus.RESOLVED.getCode()).isEqualTo(2);
        assertThat(FlowErrorLogStatus.RETRY_FAILED.getCode()).isEqualTo(3);
        assertThat(FlowFillBatchStatus.DRAFT.getCode()).isEqualTo("DRAFT");
        assertThat(FlowFillBatchStatus.PUBLISHED.getCode()).isEqualTo("PUBLISHED");
        assertThat(FlowFormInstanceStatus.RUNNING.getCode()).isEqualTo("RUNNING");
        assertThat(FlowFormInstanceStatus.REJECTED.getCode()).isEqualTo("REJECTED");
        assertThat(FlowEnableStatus.ENABLED.getCode()).isEqualTo(1);
        assertThat(FlowFormPublishStatus.DRAFT.getCode()).isZero();
        assertThat(FlowFillItemStatus.SUBMITTED.getCode()).isEqualTo("SUBMITTED");
        assertThat(FlowDiagramStatus.PENDING.getCode()).isEqualTo("pending");
    }
}
