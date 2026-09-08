package com.agentsystem.financial.controller;

import com.agentsystem.financial.FinanceInnerClient;

import com.agentsystem.user.entity.UserPreference;
import com.agentsystem.user.service.UserPreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class FinancialControllerTest {

    @Mock FinanceInnerClient    client;
    @Mock UserPreferenceService prefService;
    @Mock HttpServletRequest    request;
    @InjectMocks FinancialController controller;

    private void stubUuid(String uuid) {
        when(request.getAttribute("authenticatedUserUuid")).thenReturn(uuid);
    }

    private UserPreference prefWith(String currency) {
        UserPreference pref = new UserPreference();
        pref.setDefaultCurrency(currency);
        return pref;
    }

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Test
    void listDeposits_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("USD"));
        when(client.listDeposits("user@test.com", "USD")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listDeposits(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void listDeposits_defaultCurrencyFallsBackToUsd() {
        stubUuid("user@test.com");
        UserPreference pref = new UserPreference(); // defaultCurrency is null
        when(prefService.getOrDefault("user@test.com")).thenReturn(pref);
        when(client.listDeposits("user@test.com", "USD")).thenReturn(List.of());

        controller.listDeposits(request);

        verify(client).listDeposits("user@test.com", "USD");
    }

    @Test
    void createDeposit_returns201() {
        stubUuid("user@test.com");
        when(client.createDeposit(eq("user@test.com"), any())).thenReturn(Map.of("id", "d-1"));

        ResponseEntity<Object> resp = controller.createDeposit(Map.of("amount", 100), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateDeposit_success_returns200() {
        stubUuid("user@test.com");
        when(client.updateDeposit(eq("dep-1"), eq("user@test.com"), any())).thenReturn(Map.of("id", "dep-1"));

        ResponseEntity<Object> resp = controller.updateDeposit("dep-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateDeposit_securityException_returns403() {
        stubUuid("user@test.com");
        when(client.updateDeposit(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateDeposit("dep-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteDeposit_success_returns204() {
        stubUuid("user@test.com");
        doNothing().when(client).deleteDeposit("dep-1", "user@test.com");

        ResponseEntity<Void> resp = controller.deleteDeposit("dep-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteDeposit_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteDeposit(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteDeposit("dep-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Test
    void listStocks_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("HKD"));
        when(client.listStocks("user@test.com", "HKD")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listStocks(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createStock_returns201() {
        stubUuid("user@test.com");
        when(client.createStock(eq("user@test.com"), any())).thenReturn(Map.of("symbol", "AAPL"));

        ResponseEntity<Object> resp = controller.createStock(Map.of("symbol", "AAPL"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateStock_success_returns200() {
        stubUuid("user@test.com");
        when(client.updateStock(eq("stk-1"), eq("user@test.com"), any())).thenReturn(Map.of("id", "stk-1"));

        ResponseEntity<Object> resp = controller.updateStock("stk-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateStock_securityException_returns403() {
        stubUuid("user@test.com");
        when(client.updateStock(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateStock("stk-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteStock_success_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteStock("stk-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteStock_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteStock(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteStock("stk-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void lookupStock_returnsName() {
        when(client.lookupStock("AAPL")).thenReturn(Map.of("name", "Apple Inc."));

        ResponseEntity<Map<String, String>> resp = controller.lookupStock("AAPL");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("name", "Apple Inc.");
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Test
    void listCrypto_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("USD"));
        when(client.listCrypto("user@test.com", "USD")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listCrypto(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createCrypto_returns201() {
        stubUuid("user@test.com");
        when(client.createCrypto(eq("user@test.com"), any())).thenReturn(Map.of("symbol", "BTC"));

        ResponseEntity<Object> resp = controller.createCrypto(Map.of("symbol", "BTC"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateCrypto_success_returns200() {
        stubUuid("user@test.com");
        when(client.updateCrypto(eq("cry-1"), eq("user@test.com"), any())).thenReturn(Map.of("id", "cry-1"));

        ResponseEntity<Object> resp = controller.updateCrypto("cry-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCrypto_securityException_returns403() {
        stubUuid("user@test.com");
        when(client.updateCrypto(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateCrypto("cry-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteCrypto_success_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteCrypto("cry-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteCrypto_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteCrypto(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteCrypto("cry-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    @Test
    void listFutures_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("USD"));
        when(client.listFutures("user@test.com", "USD")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listFutures(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createFuture_returns201() {
        stubUuid("user@test.com");
        when(client.createFuture(eq("user@test.com"), any())).thenReturn(Map.of("exchangeKind", "SECURITY"));

        ResponseEntity<Object> resp = controller.createFuture(Map.of("exchangeKind", "SECURITY"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateFuture_success_returns200() {
        stubUuid("user@test.com");
        when(client.updateFuture(eq("fut-1"), eq("user@test.com"), any())).thenReturn(Map.of("id", "fut-1"));

        ResponseEntity<Object> resp = controller.updateFuture("fut-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateFuture_securityException_returns403() {
        stubUuid("user@test.com");
        when(client.updateFuture(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateFuture("fut-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteFuture_success_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteFuture("fut-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteFuture_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteFuture(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteFuture("fut-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Test
    void listCards_returnsOk() {
        stubUuid("user@test.com");
        when(client.listCards("user@test.com")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listCards(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createCard_returns201() {
        stubUuid("user@test.com");
        when(client.createCard(eq("user@test.com"), any())).thenReturn(Map.of("name", "Visa"));

        ResponseEntity<Object> resp = controller.createCard(Map.of("name", "Visa"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateCard_success_returns200() {
        stubUuid("user@test.com");
        when(client.updateCard(eq("card-1"), eq("user@test.com"), any())).thenReturn(Map.of("id", "card-1"));

        ResponseEntity<Object> resp = controller.updateCard("card-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCard_securityException_returns403() {
        stubUuid("user@test.com");
        when(client.updateCard(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateCard("card-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteCard_success_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteCard("card-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteCard_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteCard(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteCard("card-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @Test
    void listSalary_returnsOk() {
        stubUuid("user@test.com");
        Map<String, Object> row = Map.of("id", "s-1", "ownerUuid", "user@test.com",
                "year", 2025, "month", 6, "region", "HK", "currency", "HKD");
        when(client.listSalary("user@test.com")).thenReturn(List.of(row));

        ResponseEntity<List<Object>> resp = controller.listSalary(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void createSalary_returns201() {
        stubUuid("user@test.com");
        Map<String, Object> record = Map.of("id", "s-2", "ownerUuid", "user@test.com");
        when(client.createSalary(eq("user@test.com"), any())).thenReturn(record);

        ResponseEntity<Object> resp = controller.createSalary(Map.of("year", 2025), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isEqualTo(record);
    }

    @Test
    void updateSalary_ownerMatch_returns200() {
        stubUuid("user@test.com");
        Map<String, Object> updated = Map.of("id", "s-1");
        when(client.updateSalary(eq("s-1"), eq("user@test.com"), any())).thenReturn(updated);

        ResponseEntity<Object> resp = controller.updateSalary("s-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateSalary_wrongOwner_returns403() {
        stubUuid("other@test.com");
        when(client.updateSalary(eq("s-1"), eq("other@test.com"), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Object> resp = controller.updateSalary("s-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteSalary_ownerMatch_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteSalary("s-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(client).deleteSalary("s-1", "user@test.com");
    }

    @Test
    void deleteSalary_wrongOwner_returns403() {
        stubUuid("other@test.com");
        doThrow(new SecurityException("not owner")).when(client).deleteSalary("s-1", "other@test.com");

        ResponseEntity<Void> resp = controller.deleteSalary("s-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @Test
    void refreshPrices_returnsOkWithStatus() {
        stubUuid("user@test.com");
        doNothing().when(client).refreshPrices("user@test.com");

        ResponseEntity<Map<String, String>> resp = controller.refreshPrices(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "ok");
    }

    @Test
    void ownerUuid_noAttribute_usesAnonymous() {
        when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        when(prefService.getOrDefault("anonymous")).thenReturn(prefWith("USD"));
        when(client.listDeposits("anonymous", "USD")).thenReturn(List.of());

        ResponseEntity<List<Object>> resp = controller.listDeposits(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(client).listDeposits("anonymous", "USD");
    }
}
