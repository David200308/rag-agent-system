package com.agentsystem.webrisk.service;

import com.agentsystem.config.WebRiskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleWebRiskClientTest {

    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parse_emptyObject_isClean() throws Exception {
        GoogleWebRiskClient.LookupResult r = GoogleWebRiskClient.parse(mapper.readTree("{}"));

        assertThat(r.threatTypes()).isEmpty();
        assertThat(r.expireTime()).isNull();
    }

    @Test
    void parse_threat_readsTypesAndExpireTime() throws Exception {
        GoogleWebRiskClient.LookupResult r = GoogleWebRiskClient.parse(mapper.readTree("""
                {"threat":{"threatTypes":["MALWARE","SOCIAL_ENGINEERING"],
                           "expireTime":"2026-10-06T15:01:23.045123456Z"}}"""));

        assertThat(r.threatTypes()).containsExactly("MALWARE", "SOCIAL_ENGINEERING");
        assertThat(r.expireTime()).isEqualTo(Instant.parse("2026-10-06T15:01:23.045123456Z"));
    }

    @Test
    void errorDetail_surfacesGoogleStatusAndMessage() {
        GoogleWebRiskClient client = new GoogleWebRiskClient(
                new WebRiskProperties(true, "k", 0, 0, 0, 0), mapper);

        assertThat(client.errorDetail(
                "{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\","
                        + "\"message\":\"Requests to this API webrisk method are blocked.\"}}"))
                .isEqualTo(": PERMISSION_DENIED Requests to this API webrisk method are blocked.");
        assertThat(client.errorDetail("not json")).isEmpty();
    }

    @Test
    void parse_threatWithBadExpireTime_keepsTypesAndNullExpiry() throws Exception {
        GoogleWebRiskClient.LookupResult r = GoogleWebRiskClient.parse(mapper.readTree("""
                {"threat":{"threatTypes":["UNWANTED_SOFTWARE"],"expireTime":"not-a-date"}}"""));

        assertThat(r.threatTypes()).containsExactly("UNWANTED_SOFTWARE");
        assertThat(r.expireTime()).isNull();
    }
}
