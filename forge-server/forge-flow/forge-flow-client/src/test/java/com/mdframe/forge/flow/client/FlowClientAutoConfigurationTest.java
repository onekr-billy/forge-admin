package com.mdframe.forge.flow.client;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlowClientAutoConfiguration.class));

    @Test
    void defaultFlowRestTemplateUsesConfiguredConnectionPool() {
        contextRunner.withPropertyValues(
                        "forge.flow.client.connect-timeout=1200",
                        "forge.flow.client.read-timeout=2300",
                        "forge.flow.client.connection-request-timeout=700",
                        "forge.flow.client.keep-alive-duration=45000",
                        "forge.flow.client.max-connections=71",
                        "forge.flow.client.max-connections-per-route=29",
                        "forge.flow.client.validate-after-inactivity=4100")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean("flowRestTemplate", RestTemplate.class);
                    assertThat(restTemplate.getRequestFactory())
                            .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);

                    PoolingHttpClientConnectionManager connectionManager = context.getBean(
                            "flowHttpConnectionManager", PoolingHttpClientConnectionManager.class);
                    assertThat(connectionManager.getMaxTotal()).isEqualTo(71);
                    assertThat(connectionManager.getDefaultMaxPerRoute()).isEqualTo(29);

                    ConnectionConfig connectionConfig = context.getBean(
                            "flowHttpConnectionConfig", ConnectionConfig.class);
                    assertThat(connectionConfig.getConnectTimeout().toMilliseconds()).isEqualTo(1200L);
                    assertThat(connectionConfig.getValidateAfterInactivity().toMilliseconds()).isEqualTo(4100L);
                    assertThat(connectionConfig.getTimeToLive().toMilliseconds()).isEqualTo(45000L);

                    RequestConfig requestConfig = context.getBean("flowHttpRequestConfig", RequestConfig.class);
                    assertThat(requestConfig.getResponseTimeout().toMilliseconds()).isEqualTo(2300L);
                    assertThat(requestConfig.getConnectionRequestTimeout().toMilliseconds()).isEqualTo(700L);
                    assertThat(requestConfig.getConnectionKeepAlive().toMilliseconds()).isEqualTo(45000L);
                });
    }

    @Test
    void customFlowRestTemplateStillOverridesDefault() {
        RestTemplate custom = new RestTemplate();
        contextRunner.withBean("flowRestTemplate", RestTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean("flowRestTemplate")).isSameAs(custom));
    }
}
