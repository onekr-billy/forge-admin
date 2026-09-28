package com.mdframe.forge.flow.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClientPropertiesTest {

    @Test
    void shouldUseRepositoryFlowServerPortByDefault() {
        assertThat(new FlowClientProperties().getUrl()).isEqualTo("http://localhost:8081");
    }

    @Test
    void standaloneClientShouldUseRepositoryFlowServerPortByDefault() throws Exception {
        FlowClient client = new FlowClient();
        Field serviceUrl = FlowClient.class.getDeclaredField("flowServiceUrl");
        serviceUrl.setAccessible(true);

        assertThat(serviceUrl.get(client)).isEqualTo("http://localhost:8081");
    }
}
