package com.agentsystem.alert.service;

import com.agentsystem.config.AlertProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AlertRuleClientTest {

    AlertRuleClient client;
    MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        AlertProperties props = new AlertProperties("alert-key", "http://alert-task");
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("X-Alert-Key", props.serviceKey());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AlertRuleClient(props);
        // Swap in the mock-bound RestClient built above.
        org.springframework.test.util.ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    @Test
    void createPriceRule_sendsExpectedRequestAndParsesResponse() {
        server.expect(requestTo("http://alert-task/internal/alerts/price"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Alert-Key", "alert-key"))
                .andRespond(withSuccess("{\"id\":\"rule-1\",\"symbol\":\"BTC/USD\"}", org.springframework.http.MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.createPriceRule(
                "owner-1", "org-1", "BTC/USD", "feed-1", "CRYPTO", 100.0, ">=", null);

        assertThat(result).containsEntry("id", "rule-1").containsEntry("symbol", "BTC/USD");
        server.verify();
    }

    @Test
    void listRules_returnsParsedMap() {
        server.expect(requestTo("http://alert-task/internal/alerts?ownerUuid=owner-1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("{\"price\":[],\"defi\":[],\"predictMarket\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.listRules("owner-1");

        assertThat(result).containsKeys("price", "defi", "predictMarket");
        server.verify();
    }

    @Test
    void deleteRule_sendsOwnerUuidAsQueryParam() {
        server.expect(requestTo("http://alert-task/internal/alerts/price/rule-1?ownerUuid=owner-1"))
                .andExpect(method(org.springframework.http.HttpMethod.DELETE))
                .andRespond(withNoContent());

        client.deleteRule("price", "rule-1", "owner-1");

        server.verify();
    }

    @Test
    void deleteRule_notFound_throws() {
        server.expect(requestTo("http://alert-task/internal/alerts/price/missing?ownerUuid=owner-1"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.deleteRule("price", "missing", "owner-1"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void updateRule_mergesOwnerUuidIntoBody() {
        server.expect(requestTo("http://alert-task/internal/alerts/price/rule-1"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(jsonPath("$.ownerUuid").value("owner-1"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andRespond(withSuccess("{\"id\":\"rule-1\",\"enabled\":false}", org.springframework.http.MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.updateRule("price", "rule-1", "owner-1", Map.of("enabled", false));

        assertThat(result).containsEntry("enabled", false);
        server.verify();
    }
}
