package com.agentsystem.webfetch.service.impl;

import com.agentsystem.webfetch.service.WebFetchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LightpandaWebFetchServiceTest {

    @Test
    void toIpLiteralEndpoint_ipv4LiteralIsKept() {
        assertThat(LightpandaWebFetchService.toIpLiteralEndpoint("ws://10.1.2.3:9222").toString())
                .isEqualTo("ws://10.1.2.3:9222");
    }

    @Test
    void toIpLiteralEndpoint_hostnameIsResolvedToAnIpLiteral_keepingPortAndPath() {
        String endpoint = LightpandaWebFetchService.toIpLiteralEndpoint("ws://localhost:9222/devtools").toString();

        assertThat(endpoint).matches("ws://(127\\.0\\.0\\.1|\\[[0-9a-f:]+\\]):9222/devtools");
    }

    @Test
    void toIpLiteralEndpoint_ipv6IsBracketed() {
        assertThat(LightpandaWebFetchService.toIpLiteralEndpoint("ws://[::1]:9222").toString())
                .isEqualTo("ws://[0:0:0:0:0:0:0:1]:9222");
    }

    @Test
    void toIpLiteralEndpoint_unresolvableHost_throwsClearError() {
        assertThatThrownBy(() -> LightpandaWebFetchService.toIpLiteralEndpoint("ws://no-such-host.invalid:9222"))
                .isInstanceOf(WebFetchException.class)
                .hasMessageContaining("no-such-host.invalid");
    }
}
