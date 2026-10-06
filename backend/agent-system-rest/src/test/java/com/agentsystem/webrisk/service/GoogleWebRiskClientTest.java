package com.agentsystem.webrisk.service;

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
    void parse_threatWithBadExpireTime_keepsTypesAndNullExpiry() throws Exception {
        GoogleWebRiskClient.LookupResult r = GoogleWebRiskClient.parse(mapper.readTree("""
                {"threat":{"threatTypes":["UNWANTED_SOFTWARE"],"expireTime":"not-a-date"}}"""));

        assertThat(r.threatTypes()).containsExactly("UNWANTED_SOFTWARE");
        assertThat(r.expireTime()).isNull();
    }
}
