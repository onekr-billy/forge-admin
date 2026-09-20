package com.mdframe.forge.flow.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdframe.forge.flow.client.job.JobFlowRemoteProperties;
import com.mdframe.forge.flow.client.job.RemoteJobFlowExecutor;
import com.mdframe.forge.starter.job.flow.JobFlowExecutor;
import com.mdframe.forge.starter.outbound.client.SecureOutboundClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 流程客户端 Spring Boot 自动配置
 * <p>
 * 引入 forge-flow-client 依赖后自动生效，无需手动 {@code @Bean}。
 * 如需自定义，可自行声明 {@link FlowClient} Bean 覆盖此处默认实现。
 *
 * @author forge
 */
@AutoConfiguration
@EnableConfigurationProperties({FlowClientProperties.class, JobFlowRemoteProperties.class})
public class FlowClientAutoConfiguration {

    /**
     * 默认 Token 透传实现：从当前 HTTP 请求头读取 Authorization
     * <p>
     * 当引入了 forge-starter-auth 时，该模块会注册更高级的 SaTokenFlowTokenProvider，
     * 此默认实现因 {@code @ConditionalOnMissingBean} 而不会生效，无需担心冲突。
     * 在非 Web 上下文（定时任务等）中获取失败时安全返回 null，降级使用静态 token 配置。
     */
    @Bean
    @ConditionalOnMissingBean(FlowTokenProvider.class)
    @ConditionalOnClass(name = "org.springframework.web.context.request.RequestContextHolder")
    public FlowTokenProvider requestContextFlowTokenProvider() {
        return new RequestContextFlowTokenProvider();
    }

    @Bean("flowHttpConnectionConfig")
    @ConditionalOnMissingBean(name = "flowHttpConnectionConfig")
    public ConnectionConfig flowHttpConnectionConfig(FlowClientProperties properties) {
        return ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeout()))
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(properties.getValidateAfterInactivity()))
                .setTimeToLive(TimeValue.ofMilliseconds(properties.getKeepAliveDuration()))
                .build();
    }

    @Bean("flowHttpConnectionManager")
    @ConditionalOnMissingBean(name = "flowHttpConnectionManager")
    public PoolingHttpClientConnectionManager flowHttpConnectionManager(
            FlowClientProperties properties,
            @Qualifier("flowHttpConnectionConfig") ConnectionConfig connectionConfig) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(properties.getMaxConnections())
                .setMaxConnPerRoute(properties.getMaxConnectionsPerRoute())
                .setDefaultConnectionConfig(connectionConfig)
                .build();
    }

    @Bean("flowHttpRequestConfig")
    @ConditionalOnMissingBean(name = "flowHttpRequestConfig")
    public RequestConfig flowHttpRequestConfig(FlowClientProperties properties) {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectionRequestTimeout()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeout()))
                .setConnectionKeepAlive(TimeValue.ofMilliseconds(properties.getKeepAliveDuration()))
                .build();
    }

    @Bean(value = "flowHttpClient", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "flowHttpClient")
    public CloseableHttpClient flowHttpClient(
            @Qualifier("flowHttpConnectionManager") PoolingHttpClientConnectionManager connectionManager,
            @Qualifier("flowHttpRequestConfig") RequestConfig requestConfig,
            FlowClientProperties properties) {
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(properties.getKeepAliveDuration()))
                .build();
    }

    @Bean("flowClientHttpRequestFactory")
    @ConditionalOnMissingBean(name = "flowClientHttpRequestFactory")
    public HttpComponentsClientHttpRequestFactory flowClientHttpRequestFactory(
            @Qualifier("flowHttpClient") CloseableHttpClient httpClient,
            FlowClientProperties properties) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectionRequestTimeout(Duration.ofMillis(properties.getConnectionRequestTimeout()));
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeout()));
        return factory;
    }

    /**
     * 注册使用专用连接池的 RestTemplate。
     * <p>
     * 仅在容器中不存在名为 {@code flowRestTemplate} 的 Bean 时生效。
     */
    @Bean("flowRestTemplate")
    @ConditionalOnMissingBean(name = "flowRestTemplate")
    public RestTemplate flowRestTemplate(
            @Qualifier("flowClientHttpRequestFactory") ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

    /**
     * 注册 FlowClient
     * <p>
     * 如果容器中存在 {@link FlowTokenProvider} Bean（如引入 forge-starter-auth 自动注入的
     * {@code SaTokenFlowTokenProvider}），则自动启用动态 Token 透传机制。
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowClient flowClient(FlowClientProperties properties,
                                 @Qualifier("flowRestTemplate") RestTemplate flowRestTemplate,
                                 ObjectProvider<FlowTokenProvider> tokenProviderObjectProvider) {
        FlowClient flowClient = new FlowClient(flowRestTemplate, properties.getUrl(), properties.getToken());
        // 自动注入 TokenProvider（存在时）
        FlowTokenProvider tokenProvider = tokenProviderObjectProvider.getIfAvailable();
        if (tokenProvider != null) {
            flowClient.setTokenProvider(tokenProvider);
        }
        return flowClient;
    }

    @Bean
    @ConditionalOnProperty(prefix = "forge.flow.job.remote", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(JobFlowExecutor.class)
    public JobFlowExecutor remoteJobFlowExecutor(
            SecureOutboundClient outboundClient,
            ObjectMapper objectMapper,
            JobFlowRemoteProperties properties) {
        return new RemoteJobFlowExecutor(outboundClient, objectMapper, properties);
    }
}
