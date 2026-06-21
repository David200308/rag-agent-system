package com.agentsystem.auth.service;

import com.agentsystem.auth.ClientIdentityProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIdentityServiceTest {

    private static final String IOS_SECRET = "test-ios-secret";
    private static final String WEB_SECRET = "test-web-secret";

    private ClientIdentityService service(String ios, String web) {
        return new ClientIdentityService(new ClientIdentityProperties(false, ios, web));
    }

    // ── verifyIos ─────────────────────────────────────────────────────────────

    @Test
    void verifyIos_validSignature_returnsTrue() throws Exception {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);
        long ts = Instant.now().getEpochSecond();
        String version = "1.0.0";
        String method  = "GET";
        String path    = "/api/v1/agent/query";

        String message  = "ios:" + version + ":" + method.toUpperCase() + ":" + path + ":" + ts;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(IOS_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getEncoder().encodeToString(
                mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));

        assertThat(svc.verifyIos(sig, String.valueOf(ts), version, method, path)).isTrue();
    }

    @Test
    void verifyIos_wrongSignature_returnsFalse() {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);
        long ts = Instant.now().getEpochSecond();

        assertThat(svc.verifyIos("invalidsig==", String.valueOf(ts), "1.0.0", "GET", "/api/test")).isFalse();
    }

    @Test
    void verifyIos_staleTimestamp_returnsFalse() throws Exception {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);
        long staleTs = Instant.now().getEpochSecond() - 600;  // > 5 min ago

        assertThat(svc.verifyIos("any", String.valueOf(staleTs), "1.0.0", "GET", "/api/test")).isFalse();
    }

    @Test
    void verifyIos_noIosSecret_returnsFalse() {
        ClientIdentityService svc = service(null, WEB_SECRET);
        long ts = Instant.now().getEpochSecond();

        assertThat(svc.verifyIos("sig", String.valueOf(ts), "1.0.0", "GET", "/api/test")).isFalse();
    }

    @Test
    void verifyIos_blankIosSecret_returnsFalse() {
        ClientIdentityService svc = service("  ", WEB_SECRET);
        long ts = Instant.now().getEpochSecond();

        assertThat(svc.verifyIos("sig", String.valueOf(ts), "1.0.0", "GET", "/api/test")).isFalse();
    }

    @Test
    void verifyIos_invalidTimestampFormat_returnsFalse() {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);

        assertThat(svc.verifyIos("sig", "not-a-number", "1.0.0", "GET", "/api/test")).isFalse();
    }

    // ── verifyWebToken ────────────────────────────────────────────────────────

    @Test
    void verifyWebToken_correctToken_returnsTrue() {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);

        assertThat(svc.verifyWebToken(WEB_SECRET)).isTrue();
    }

    @Test
    void verifyWebToken_wrongToken_returnsFalse() {
        ClientIdentityService svc = service(IOS_SECRET, WEB_SECRET);

        assertThat(svc.verifyWebToken("wrong-secret")).isFalse();
    }

    @Test
    void verifyWebToken_noWebSecret_returnsFalse() {
        ClientIdentityService svc = service(IOS_SECRET, null);

        assertThat(svc.verifyWebToken("any-token")).isFalse();
    }

    @Test
    void verifyWebToken_blankWebSecret_returnsFalse() {
        ClientIdentityService svc = service(IOS_SECRET, "");

        assertThat(svc.verifyWebToken("any-token")).isFalse();
    }
}
