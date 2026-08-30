package com.mdframe.forge.starter.flow.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTaskStatusTest {

    @Test
    void shouldKeepStableDatabaseCodes() {
        assertThat(FlowTaskStatus.PENDING.getCode()).isZero();
        assertThat(FlowTaskStatus.CLAIMED.getCode()).isEqualTo(1);
        assertThat(FlowTaskStatus.APPROVED.getCode()).isEqualTo(2);
        assertThat(FlowTaskStatus.REJECTED.getCode()).isEqualTo(3);
        assertThat(FlowTaskStatus.DELEGATED.getCode()).isEqualTo(4);
        assertThat(FlowTaskStatus.CANCELED.getCode()).isEqualTo(5);
        assertThat(FlowTaskStatus.WITHDRAWN.getCode()).isEqualTo(6);
        assertThat(FlowTaskStatus.RETURNED.getCode()).isEqualTo(7);
        assertThat(FlowTaskStatus.TERMINATED.getCode()).isEqualTo(8);
    }

    @Test
    void shouldResolveHistoryActionAndActionableStatuses() {
        assertThat(FlowTaskStatus.historyActionOf(FlowTaskStatus.REJECTED.getCode())).isEqualTo("reject");
        assertThat(FlowTaskStatus.historyActionOf(null)).isEqualTo("pending");
        assertThat(FlowTaskStatus.isActionable(FlowTaskStatus.PENDING.getCode())).isTrue();
        assertThat(FlowTaskStatus.isActionable(FlowTaskStatus.APPROVED.getCode())).isFalse();
        assertThat(FlowTaskStatus.TODO_CODES).containsExactly(0, 1);
        assertThat(FlowTaskStatus.DONE_CODES).containsExactly(2, 3, 4, 5, 7, 8);
    }
}
