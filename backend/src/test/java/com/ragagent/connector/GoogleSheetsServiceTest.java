package com.ragagent.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleSheetsServiceTest {

    @Mock ConnectorTokenRepository tokenRepo;
    @Mock RestClient.Builder       restClientBuilder;

    private final ConnectorProperties props = new ConnectorProperties(
            new ConnectorProperties.Google("g-client-id", "g-secret"),
            new ConnectorProperties.Figma("f-client-id",  "f-secret"),
            null,
            "https://app.example.com"
    );

    GoogleSheetsService service;

    @BeforeEach
    void setUp() {
        service = new GoogleSheetsService(tokenRepo, props, restClientBuilder);
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

    // ── createSpreadsheet — guard: not connected ──────────────────────────────

    @Test
    void createSpreadsheet_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createSpreadsheet("My Sheet", "col1,col2\nval1,val2", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── readSpreadsheet — guard: not connected ────────────────────────────────

    @Test
    void readSpreadsheet_noToken_throwsIllegalState() {
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.readSpreadsheet("https://docs.google.com/spreadsheets/d/abc123/edit", "user@test.com", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not connected");
    }

    // ── non-expiring token passes the guard ───────────────────────────────────

    @Test
    void createSpreadsheet_nonExpiringToken_passesThroughToHttp() {
        ConnectorToken token = ConnectorToken.builder()
                .ownerEmail("user@test.com")
                .provider("google")
                .accessToken("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepo.findByOwnerEmailAndProviderAndOrgIdIsNull("user@test.com", "google"))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.createSpreadsheet("Title", "a,b", "user@test.com", null))
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

    // ── extractSheetId via reflection ─────────────────────────────────────────

    @Test
    void extractSheetId_fullUrl_extractsId() throws Exception {
        String id = callExtractSheetId("https://docs.google.com/spreadsheets/d/abc123xyz/edit");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractSheetId_rawId_returnsAsIs() throws Exception {
        String id = callExtractSheetId("abc123xyz");
        assertThat(id).isEqualTo("abc123xyz");
    }

    @Test
    void extractSheetId_urlWithQueryParams_extractsCorrectly() throws Exception {
        String id = callExtractSheetId(
                "https://docs.google.com/spreadsheets/d/1ABC-xyz_789/edit#gid=0");
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

    // ── parseRows via reflection ──────────────────────────────────────────────

    @Test
    void parseRows_tabSeparated_returnsCells() throws Exception {
        List<List<Object>> rows = callParseRows("Name\tAge\nAlice\t30");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("Name", "Age");
        assertThat(rows.get(1)).containsExactly("Alice", "30");
    }

    @Test
    void parseRows_commaSeparated_returnsCells() throws Exception {
        List<List<Object>> rows = callParseRows("Name,Age\nBob,25");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("Name", "Age");
        assertThat(rows.get(1)).containsExactly("Bob", "25");
    }

    @Test
    void parseRows_emptyLines_skipped() throws Exception {
        List<List<Object>> rows = callParseRows("A,B\n\nC,D");
        assertThat(rows).hasSize(2);
    }

    @Test
    void parseRows_singleLine_returnsSingleRow() throws Exception {
        List<List<Object>> rows = callParseRows("hello,world");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactly("hello", "world");
    }

    @Test
    void parseRows_emptyContent_returnsWrappedContent() throws Exception {
        // When all lines are blank, returns List.of(List.of(content))
        List<List<Object>> rows = callParseRows("   \n   ");
        assertThat(rows).hasSize(1);
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private String callExtractSheetId(String input) throws Exception {
        Method m = GoogleSheetsService.class.getDeclaredMethod("extractSheetId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, input);
    }

    private boolean callIsExpiringSoon(ConnectorToken ct) throws Exception {
        Method m = GoogleSheetsService.class.getDeclaredMethod("isExpiringSoon", ConnectorToken.class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, ct);
    }

    @SuppressWarnings("unchecked")
    private List<List<Object>> callParseRows(String content) throws Exception {
        Method m = GoogleSheetsService.class.getDeclaredMethod("parseRows", String.class);
        m.setAccessible(true);
        return (List<List<Object>>) m.invoke(service, content);
    }
}
