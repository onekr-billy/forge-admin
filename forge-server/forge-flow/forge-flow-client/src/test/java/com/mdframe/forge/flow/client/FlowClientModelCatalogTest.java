package com.mdframe.forge.flow.client;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowClientModelCatalogTest {

    @Test
    void designCatalogExplicitlyRequestsDraftModels() {
        RestTemplate restTemplate = successfulRestTemplate();
        FlowClient client = new FlowClient(restTemplate, "http://flow");

        client.getModelList(null, null);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertThat(url.getValue()).isEqualTo("http://flow/api/flow/model/list?includeDraft=true");
    }

    @Test
    void publishedCatalogRequestsDeployedStatusOnly() {
        RestTemplate restTemplate = successfulRestTemplate();
        FlowClient client = new FlowClient(restTemplate, "http://flow");

        client.getModelList("approval", 1);

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(url.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertThat(url.getValue()).contains("category=approval", "status=1").doesNotContain("includeDraft");
    }

    private RestTemplate successfulRestTemplate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"code\":200,\"msg\":\"ok\",\"data\":[]}", HttpStatus.OK));
        return restTemplate;
    }
}
