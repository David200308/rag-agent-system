package com.agentsystem.connector.service;

import com.agentsystem.connector.service.impl.GoogleSlidesServiceImpl;

import com.agentsystem.connector.ConnectorProperties;
import com.agentsystem.connector.entity.ConnectorToken;
import com.agentsystem.connector.repository.ConnectorTokenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleSlidesServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            null,
            "https://app.example.com"
    );

    GoogleSlidesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GoogleSlidesServiceImpl(tokenRepo, props, restClientBuilder);
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    void isConnected_tokenPresent_returnsTrue() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com", null)).isTrue();
    }

    @Test
    void isConnected_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com", null)).isFalse();
    }

    @Test
    void isConnected_nullEmail_checksEmptyStringInRepo() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("", "google"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected(null, null)).isFalse();
    }

    // ── createPresentation — guard: not connected ─────────────────────────────

    @Test
    void createPresentation_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createPresentation("My Deck", "Slide 1\nbody text\n---\nSlide 2", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readPresentation — guard: not connected ───────────────────────────────

    @Test
    void readPresentation_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readPresentation("https://docs.google.com/presentation/d/abc123/edit", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── non-expiring token passes the guard ───────────────────────────────────

    @Test
    void createPresentation_nonExpiringToken_passesThroughToHttp() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.createPresentation("Title", "Slide 1", "user@test.com", null))
                .isNotInstanceOf(IllegalStateException.class);
    }

    // ── isConnected with orgId ────────────────────────────────────────────────

    @Test
    void isConnected_withOrgId_usesOrgScopedRepo() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgId("user@test.com", "google", "org-1"))
                .thenReturn(Optional.of(ConnectorToken.builder().build()));

        assertThat(service.isConnected("user@test.com", "org-1")).isTrue();
    }

    @Test
    void isConnected_withOrgId_noToken_returnsFalse() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgId("user@test.com", "google", "org-1"))
                .thenReturn(Optional.empty());

        assertThat(service.isConnected("user@test.com", "org-1")).isFalse();
    }

    // ── extractPresId via reflection ──────────────────────────────────────────

    @Test
    void extractPresId_fullUrl_extractsId() throws Exception {
        String id = callExtractPresId("https://docs.google.com/presentation/d/abc123xyz/edit");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractPresId_rawId_returnsAsIs() throws Exception {
        String id = callExtractPresId("abc123xyz");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractPresId_urlWithDashesAndUnderscores_extractsCorrectly() throws Exception {
        String id = callExtractPresId(
                "https://docs.google.com/presentation/d/1ABC-xyz_789/edit?usp=sharing");
        assertThat(id).isEqualTo("1ABC-xyz_789");
    }

    // ── isExpiringSoon via reflection ─────────────────────────────────────────

    @Test
    void isExpiringSoon_tokenExpiresInOneHour_returnsFalse() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        assertThat(callIsExpiringSoon(ct)).isFalse();
    }

    @Test
    void isExpiringSoon_tokenAlreadyExpired_returnsTrue() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build();
        assertThat(callIsExpiringSoon(ct)).isTrue();
    }

    @Test
    void isExpiringSoon_tokenExpiresIn100Seconds_returnsTrue() throws Exception {
        ConnectorToken ct = ConnectorToken.builder()
                .expiresAt(LocalDateTime.now().plusSeconds(100))
                .build();
        assertThat(callIsExpiringSoon(ct)).isTrue();
    }

    @Test
    void isExpiringSoon_nullExpiresAt_returnsFalse() throws Exception {
        ConnectorToken ct = ConnectorToken.builder().expiresAt(null).build();
        assertThat(callIsExpiringSoon(ct)).isFalse();
    }

    // ── buildSlideRequests via reflection ─────────────────────────────────────

    @Test
    void buildSlideRequests_nullContent_returnsEmpty() throws Exception {
        List<Map<String, Object>> result = callBuildSlideRequests(null, "slide-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildSlideRequests_blankContent_returnsEmpty() throws Exception {
        List<Map<String, Object>> result = callBuildSlideRequests("   ", "slide-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildSlideRequests_singleSlide_withFirstSlideId_hasDeleteAndAddRequests() throws Exception {
        List<Map<String, Object>> result = callBuildSlideRequests("Title\nBody text", "slide-id-1");
        // Expect: deleteObject + insertSlide + createShape(title) + insertText(title)
        //          + createShape(body) + insertText(body) = 6 requests
        assertThat(result).isNotEmpty();
        // First request should be deleteObject (replacing first slide)
        assertThat(result.get(0)).containsKey("deleteObject");
    }

    @Test
    void buildSlideRequests_singleSlide_noFirstSlideId_doesNotDelete() throws Exception {
        List<Map<String, Object>> result = callBuildSlideRequests("Title\nBody text", null);
        assertThat(result).isNotEmpty();
        // No deleteObject since firstSlideId is null
        assertThat(result.get(0)).doesNotContainKey("deleteObject");
        assertThat(result.get(0)).containsKey("insertSlide");
    }

    @Test
    void buildSlideRequests_multipleSlides_createsMultipleSets() throws Exception {
        String content = "Slide 1 Title\nSlide 1 body\n---\nSlide 2 Title\nSlide 2 body";
        List<Map<String, Object>> result = callBuildSlideRequests(content, "first-slide");
        // First slide = delete + insertSlide + 2x createShape + 2x insertText = 6
        // Second slide = insertSlide + 2x createShape + 2x insertText = 5
        // Total >= 11
        assertThat(result.size()).isGreaterThanOrEqualTo(11);
    }

    @Test
    void buildSlideRequests_slideTitleOnly_noBody_fewerRequests() throws Exception {
        String content = "Title Only";
        List<Map<String, Object>> result = callBuildSlideRequests(content, null);
        // insertSlide + createShape(title) + insertText(title) = 3 (no body box)
        // Should not have body createShape
        long insertTextCount = result.stream().filter(r -> r.containsKey("insertText")).count();
        long createShapeCount = result.stream().filter(r -> r.containsKey("createShape")).count();
        assertThat(insertTextCount).isEqualTo(1);
        assertThat(createShapeCount).isEqualTo(1);
    }

    @Test
    void buildSlideRequests_slidesWithBlankSeparator_skipsBlankBlocks() throws Exception {
        String content = "Slide 1\nbody\n---\n\n---\nSlide 3\nbody3";
        List<Map<String, Object>> result = callBuildSlideRequests(content, null);
        // Should produce 2 slides worth of requests (blank block skipped)
        long insertSlideCount = result.stream().filter(r -> r.containsKey("insertSlide")).count();
        assertThat(insertSlideCount).isEqualTo(2);
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private String callExtractPresId(String input) throws Exception {
        Method m = GoogleSlidesServiceImpl.class.getDeclaredMethod("extractPresId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, input);
    }

    private boolean callIsExpiringSoon(ConnectorToken ct) throws Exception {
        Method m = GoogleSlidesServiceImpl.class.getDeclaredMethod("isExpiringSoon", ConnectorToken.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, ct);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callBuildSlideRequests(String content, String firstSlideId) throws Exception {
        Method m = GoogleSlidesServiceImpl.class.getDeclaredMethod(
                "buildSlideRequests", String.class, String.class);
        m.setAccessible(true);
        return (List<Map<String, Object>>) m.invoke(service, content, firstSlideId);
    }
}
