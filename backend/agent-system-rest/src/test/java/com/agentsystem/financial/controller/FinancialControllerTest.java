package com.agentsystem.financial.controller;

import com.agentsystem.financial.service.FinancialService;

import com.agentsystem.financial.dto.CardDto;
import com.agentsystem.financial.dto.CashDepositDto;
import com.agentsystem.financial.dto.CryptoInvestmentDto;
import com.agentsystem.financial.dto.SalaryUsageRecordDto;
import com.agentsystem.financial.dto.StockInvestmentDto;
import com.agentsystem.financial.entity.Card;
import com.agentsystem.financial.entity.CashDeposit;
import com.agentsystem.financial.entity.CryptoInvestment;
import com.agentsystem.financial.entity.SalaryUsageRecord;
import com.agentsystem.financial.entity.StockInvestment;
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

    @Mock FinancialService      service;
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
        when(service.listDeposits("user@test.com", "USD")).thenReturn(List.of());

        ResponseEntity<List<CashDepositDto>> resp = controller.listDeposits(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void listDeposits_defaultCurrencyFallsBackToUsd() {
        stubUuid("user@test.com");
        UserPreference pref = new UserPreference(); // defaultCurrency is null
        when(prefService.getOrDefault("user@test.com")).thenReturn(pref);
        when(service.listDeposits("user@test.com", "USD")).thenReturn(List.of());

        controller.listDeposits(request);

        verify(service).listDeposits("user@test.com", "USD");
    }

    @Test
    void createDeposit_returns201() {
        stubUuid("user@test.com");
        when(service.createDeposit(eq("user@test.com"), any())).thenReturn(new CashDeposit());

        ResponseEntity<CashDeposit> resp = controller.createDeposit(Map.of("amount", 100), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateDeposit_success_returns200() {
        stubUuid("user@test.com");
        when(service.updateDeposit(eq("dep-1"), eq("user@test.com"), any())).thenReturn(new CashDeposit());

        ResponseEntity<CashDeposit> resp = controller.updateDeposit("dep-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateDeposit_securityException_returns403() {
        stubUuid("user@test.com");
        when(service.updateDeposit(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<CashDeposit> resp = controller.updateDeposit("dep-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteDeposit_success_returns204() {
        stubUuid("user@test.com");
        doNothing().when(service).deleteDeposit("dep-1", "user@test.com");

        ResponseEntity<Void> resp = controller.deleteDeposit("dep-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleteDeposit_securityException_returns403() {
        stubUuid("user@test.com");
        doThrow(new SecurityException("not owner")).when(service).deleteDeposit(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteDeposit("dep-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Test
    void listStocks_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("HKD"));
        when(service.listStocks("user@test.com", "HKD")).thenReturn(List.of());

        ResponseEntity<List<StockInvestmentDto>> resp = controller.listStocks(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createStock_returns201() {
        stubUuid("user@test.com");
        when(service.createStock(eq("user@test.com"), any())).thenReturn(new StockInvestment());

        ResponseEntity<StockInvestment> resp = controller.createStock(Map.of("symbol", "AAPL"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateStock_success_returns200() {
        stubUuid("user@test.com");
        when(service.updateStock(eq("stk-1"), eq("user@test.com"), any())).thenReturn(new StockInvestment());

        ResponseEntity<StockInvestment> resp = controller.updateStock("stk-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateStock_securityException_returns403() {
        stubUuid("user@test.com");
        when(service.updateStock(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<StockInvestment> resp = controller.updateStock("stk-1", Map.of(), request);

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
        doThrow(new SecurityException("not owner")).when(service).deleteStock(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteStock("stk-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Test
    void listCrypto_returnsOk() {
        stubUuid("user@test.com");
        when(prefService.getOrDefault("user@test.com")).thenReturn(prefWith("USD"));
        when(service.listCrypto("user@test.com", "USD")).thenReturn(List.of());

        ResponseEntity<List<CryptoInvestmentDto>> resp = controller.listCrypto(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createCrypto_returns201() {
        stubUuid("user@test.com");
        when(service.createCrypto(eq("user@test.com"), any())).thenReturn(new CryptoInvestment());

        ResponseEntity<CryptoInvestment> resp = controller.createCrypto(Map.of("symbol", "BTC"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateCrypto_success_returns200() {
        stubUuid("user@test.com");
        when(service.updateCrypto(eq("cry-1"), eq("user@test.com"), any())).thenReturn(new CryptoInvestment());

        ResponseEntity<CryptoInvestment> resp = controller.updateCrypto("cry-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCrypto_securityException_returns403() {
        stubUuid("user@test.com");
        when(service.updateCrypto(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<CryptoInvestment> resp = controller.updateCrypto("cry-1", Map.of(), request);

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
        doThrow(new SecurityException("not owner")).when(service).deleteCrypto(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteCrypto("cry-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Test
    void listCards_returnsOk() {
        stubUuid("user@test.com");
        when(service.listCards("user@test.com")).thenReturn(List.of());

        ResponseEntity<List<CardDto>> resp = controller.listCards(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createCard_returns201() {
        stubUuid("user@test.com");
        when(service.createCard(eq("user@test.com"), any())).thenReturn(new Card());

        ResponseEntity<Card> resp = controller.createCard(Map.of("name", "Visa"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateCard_success_returns200() {
        stubUuid("user@test.com");
        when(service.updateCard(eq("card-1"), eq("user@test.com"), any())).thenReturn(new Card());

        ResponseEntity<Card> resp = controller.updateCard("card-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateCard_securityException_returns403() {
        stubUuid("user@test.com");
        when(service.updateCard(anyString(), anyString(), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<Card> resp = controller.updateCard("card-1", Map.of(), request);

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
        doThrow(new SecurityException("not owner")).when(service).deleteCard(anyString(), anyString());

        ResponseEntity<Void> resp = controller.deleteCard("card-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @Test
    void listSalary_returnsOk() {
        stubUuid("user@test.com");
        SalaryUsageRecordDto dto = new SalaryUsageRecordDto(
                "s-1", "user@test.com", 2025, 6, "HK", "HKD",
                null, null, null, null, null, null, null, null, null, null, null);
        when(service.listSalary("user@test.com")).thenReturn(List.of(dto));

        ResponseEntity<List<SalaryUsageRecordDto>> resp = controller.listSalary(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    void createSalary_returns201() {
        stubUuid("user@test.com");
        SalaryUsageRecord record = new SalaryUsageRecord();
        record.setId("s-2");
        record.setOwnerUuid("user@test.com");
        when(service.createSalary(eq("user@test.com"), any())).thenReturn(record);

        ResponseEntity<SalaryUsageRecord> resp = controller.createSalary(Map.of("year", 2025), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody().getId()).isEqualTo("s-2");
    }

    @Test
    void updateSalary_ownerMatch_returns200() {
        stubUuid("user@test.com");
        SalaryUsageRecord updated = new SalaryUsageRecord();
        updated.setId("s-1");
        when(service.updateSalary(eq("s-1"), eq("user@test.com"), any())).thenReturn(updated);

        ResponseEntity<SalaryUsageRecord> resp = controller.updateSalary("s-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateSalary_wrongOwner_returns403() {
        stubUuid("other@test.com");
        when(service.updateSalary(eq("s-1"), eq("other@test.com"), any()))
                .thenThrow(new SecurityException("not owner"));

        ResponseEntity<SalaryUsageRecord> resp = controller.updateSalary("s-1", Map.of(), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void deleteSalary_ownerMatch_returns204() {
        stubUuid("user@test.com");

        ResponseEntity<Void> resp = controller.deleteSalary("s-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(service).deleteSalary("s-1", "user@test.com");
    }

    @Test
    void deleteSalary_wrongOwner_returns403() {
        stubUuid("other@test.com");
        doThrow(new SecurityException("not owner")).when(service).deleteSalary("s-1", "other@test.com");

        ResponseEntity<Void> resp = controller.deleteSalary("s-1", request);

        assertThat(resp.getStatusCode().value()).isEqualTo(403);
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @Test
    void refreshPrices_returnsOkWithStatus() {
        stubUuid("user@test.com");
        doNothing().when(service).refreshPrices("user@test.com");

        ResponseEntity<Map<String, String>> resp = controller.refreshPrices(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).containsEntry("status", "ok");
    }

    @Test
    void ownerUuid_noAttribute_usesAnonymous() {
        when(request.getAttribute("authenticatedUserUuid")).thenReturn(null);
        when(prefService.getOrDefault("anonymous")).thenReturn(prefWith("USD"));
        when(service.listDeposits("anonymous", "USD")).thenReturn(List.of());

        ResponseEntity<List<CashDepositDto>> resp = controller.listDeposits(request);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(service).listDeposits("anonymous", "USD");
    }
}
