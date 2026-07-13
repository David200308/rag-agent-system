package com.agentsystem.financial.service;

import com.agentsystem.financial.service.impl.FinancialServiceImpl;

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
import com.agentsystem.financial.repository.CardRepository;
import com.agentsystem.financial.repository.CashDepositRepository;
import com.agentsystem.financial.repository.CryptoInvestmentRepository;
import com.agentsystem.financial.repository.SalaryUsageRecordRepository;
import com.agentsystem.financial.repository.StockInvestmentRepository;

import com.agentsystem.financial.entity.Card;
import com.agentsystem.financial.entity.CashDeposit;
import com.agentsystem.financial.entity.CryptoInvestment;
import com.agentsystem.financial.entity.SalaryUsageRecord;
import com.agentsystem.financial.entity.StockInvestment;
import com.agentsystem.financial.repository.CardRepository;
import com.agentsystem.financial.repository.CashDepositRepository;
import com.agentsystem.financial.repository.CryptoInvestmentRepository;
import com.agentsystem.financial.repository.SalaryUsageRecordRepository;
import com.agentsystem.financial.repository.StockInvestmentRepository;
import com.agentsystem.financial.service.ExchangeRateService;
import com.agentsystem.financial.service.MarketPriceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock CashDepositRepository       depositRepo;
    @Mock StockInvestmentRepository   stockRepo;
    @Mock CryptoInvestmentRepository  cryptoRepo;
    @Mock CardRepository              cardRepo;
    @Mock SalaryUsageRecordRepository salaryRepo;
    @Mock ExchangeRateService         fxService;
    @Mock MarketPriceService          priceService;

    @InjectMocks FinancialServiceImpl service;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Test
    void listDeposits_returnsDtosWithConvertedAmount() {
        CashDeposit d = deposit("d-1", "user@test.com", "USD", new BigDecimal("1000"));
        when(depositRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(d));
        when(fxService.convert(1000.0, "USD", "HKD")).thenReturn(7800.0);

        var result = service.listDeposits("user@test.com", "HKD");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).convertedAmount()).isEqualByComparingTo("7800.0000");
        assertThat(result.get(0).convertedCurrency()).isEqualTo("HKD");
    }

    @Test
    void createDeposit_savesWithOwnerUuid() {
        when(depositRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<CashDeposit> cap = ArgumentCaptor.forClass(CashDeposit.class);

        service.createDeposit("owner@test.com", Map.of(
                "platform", "HSBC", "platformType", "Bank", "currency", "HKD",
                "amount", "5000", "depositType", "FIXED", "countryRegion", "HK"));

        verify(depositRepo).save(cap.capture());
        assertThat(cap.getValue().getOwnerUuid()).isEqualTo("owner@test.com");
        assertThat(cap.getValue().getId()).isNotBlank();
        assertThat(cap.getValue().getPlatform()).isEqualTo("HSBC");
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void updateDeposit_ownerCanUpdate() {
        CashDeposit d = deposit("d-1", "owner@test.com", "HKD", new BigDecimal("5000"));
        when(depositRepo.findById("d-1")).thenReturn(Optional.of(d));
        when(depositRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateDeposit("d-1", "owner@test.com", Map.of("amount", "9000"));

        verify(depositRepo).save(any());
        assertThat(d.getAmount()).isEqualByComparingTo("9000");
    }

    @Test
    void updateDeposit_nonOwnerThrowsSecurityException() {
        CashDeposit d = deposit("d-1", "owner@test.com", "USD", new BigDecimal("1000"));
        when(depositRepo.findById("d-1")).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.updateDeposit("d-1", "other@test.com", Map.of()))
                .isInstanceOf(SecurityException.class);
        verify(depositRepo, never()).save(any());
    }

    @Test
    void deleteDeposit_ownerCanDelete() {
        CashDeposit d = deposit("d-1", "owner@test.com", "USD", new BigDecimal("1000"));
        when(depositRepo.findById("d-1")).thenReturn(Optional.of(d));

        service.deleteDeposit("d-1", "owner@test.com");

        verify(depositRepo).delete(d);
    }

    @Test
    void deleteDeposit_notFound_noOp() {
        when(depositRepo.findById("ghost")).thenReturn(Optional.empty());

        service.deleteDeposit("ghost", "owner@test.com");

        verify(depositRepo, never()).delete(any());
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Test
    void listStocks_freshCache_doesNotTriggerRefresh() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");
        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("AAPL")).thenReturn(Optional.empty());
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        service.listStocks("user@test.com", "USD");

        verify(priceService, never()).refreshStockPrices(any());
    }

    @Test
    void listStocks_staleCache_triggersRefresh() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");
        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(true);
        when(priceService.getStockPrice("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("AAPL")).thenReturn(Optional.empty());
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        service.listStocks("user@test.com", "USD");

        verify(priceService).refreshStockPrices(List.of("AAPL"));
    }

    @Test
    void listStocks_withLivePrice_computesPnlPercent() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");
        s.setStockAmount(new BigDecimal("10"));
        s.setInvestAmount(new BigDecimal("1000"));
        s.setCurrency("USD");

        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("AAPL")).thenReturn(Optional.of(120.0));
        when(priceService.getStockCurrency("AAPL")).thenReturn(Optional.of("USD"));
        when(fxService.convert(1200.0, "USD", "USD")).thenReturn(1200.0);
        when(fxService.convert(1000.0, "USD", "USD")).thenReturn(1000.0);

        var result = service.listStocks("user@test.com", "USD");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).pnlPercent()).isEqualTo(20.0);
    }

    @Test
    void listStocks_noPriceAvailable_nullPnl() {
        StockInvestment s = stock("s-1", "user@test.com", "UNKNOWN");
        s.setInvestAmount(new BigDecimal("500"));
        s.setCurrency("USD");

        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("UNKNOWN")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("UNKNOWN")).thenReturn(Optional.empty());
        when(fxService.convert(500.0, "USD", "USD")).thenReturn(500.0);

        var result = service.listStocks("user@test.com", "USD");

        assertThat(result.get(0).pnlPercent()).isNull();
        assertThat(result.get(0).currentValue()).isNull();
    }

    @Test
    void listStocks_withLogoAvailable_includesLogoUrl() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");

        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockLogo("AAPL")).thenReturn(Optional.of("https://static.finnhub.io/logo/aapl.png"));
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        var result = service.listStocks("user@test.com", "USD");

        assertThat(result.get(0).logoUrl()).isEqualTo("https://static.finnhub.io/logo/aapl.png");
    }

    @Test
    void listStocks_noLogoAvailable_nullLogoUrl() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");

        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("AAPL")).thenReturn(Optional.empty());
        when(priceService.getStockLogo("AAPL")).thenReturn(Optional.empty());
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        var result = service.listStocks("user@test.com", "USD");

        assertThat(result.get(0).logoUrl()).isNull();
    }

    @Test
    void createStock_savesWithOwnerUuid() {
        when(stockRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<StockInvestment> cap = ArgumentCaptor.forClass(StockInvestment.class);

        service.createStock("user@test.com", Map.of(
                "broker", "Futu", "stockType", "US_STOCK",
                "symbol", "NVDA", "name", "NVIDIA",
                "stockAmount", "5", "investAmount", "2000",
                "currency", "USD", "fee", "1.5"));

        verify(stockRepo).save(cap.capture());
        assertThat(cap.getValue().getOwnerUuid()).isEqualTo("user@test.com");
        assertThat(cap.getValue().getId()).isNotBlank();
        assertThat(cap.getValue().getSymbol()).isEqualTo("NVDA");
        assertThat(cap.getValue().getFee()).isEqualByComparingTo("1.5");
    }

    @Test
    void updateStock_nonOwnerThrowsSecurityException() {
        StockInvestment s = stock("s-1", "owner@test.com", "AAPL");
        when(stockRepo.findById("s-1")).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.updateStock("s-1", "attacker@test.com", Map.of()))
                .isInstanceOf(SecurityException.class);
        verify(stockRepo, never()).save(any());
    }

    @Test
    void deleteStock_ownerCanDelete() {
        StockInvestment s = stock("s-1", "owner@test.com", "TSLA");
        when(stockRepo.findById("s-1")).thenReturn(Optional.of(s));

        service.deleteStock("s-1", "owner@test.com");

        verify(stockRepo).delete(s);
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Test
    void listCrypto_staleCache_triggersRefresh() {
        CryptoInvestment c = crypto("c-1", "user@test.com", "BTC");
        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
        when(priceService.isCryptoStale()).thenReturn(true);
        when(priceService.getCryptoPrice("BTC")).thenReturn(Optional.empty());
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        service.listCrypto("user@test.com", "USD");

        verify(priceService).refreshCryptoPrices(List.of("BTC"));
    }

    @Test
    void listCrypto_withLivePrice_computesPnlPercent() {
        CryptoInvestment c = crypto("c-1", "user@test.com", "ETH");
        c.setAmount(new BigDecimal("2"));
        c.setInvestAmount(new BigDecimal("4000"));
        c.setCurrency("USD");

        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
        when(priceService.isCryptoStale()).thenReturn(false);
        when(priceService.getCryptoPrice("ETH")).thenReturn(Optional.of(2500.0));
        when(fxService.convert(5000.0, "USD", "USD")).thenReturn(5000.0);
        when(fxService.convert(4000.0, "USD", "USD")).thenReturn(4000.0);

        var result = service.listCrypto("user@test.com", "USD");

        assertThat(result.get(0).pnlPercent()).isEqualTo(25.0);
        assertThat(result.get(0).currentValue()).isEqualByComparingTo("5000.0000");
    }

    @Test
    void listCrypto_withLogoAvailable_includesLogoUrl() {
        CryptoInvestment c = crypto("c-1", "user@test.com", "BTC");

        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
        when(priceService.isCryptoStale()).thenReturn(false);
        when(priceService.getCryptoPrice("BTC")).thenReturn(Optional.empty());
        when(priceService.getCryptoLogo("BTC")).thenReturn(Optional.of("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png"));
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        var result = service.listCrypto("user@test.com", "USD");

        assertThat(result.get(0).logoUrl()).isEqualTo("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png");
    }

    @Test
    void listCrypto_noLogoAvailable_nullLogoUrl() {
        CryptoInvestment c = crypto("c-1", "user@test.com", "BTC");

        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
        when(priceService.isCryptoStale()).thenReturn(false);
        when(priceService.getCryptoPrice("BTC")).thenReturn(Optional.empty());
        when(priceService.getCryptoLogo("BTC")).thenReturn(Optional.empty());
        when(fxService.convert(anyDouble(), any(), any())).thenReturn(0.0);

        var result = service.listCrypto("user@test.com", "USD");

        assertThat(result.get(0).logoUrl()).isNull();
    }

    @Test
    void createCrypto_savesCorrectly() {
        when(cryptoRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<CryptoInvestment> cap = ArgumentCaptor.forClass(CryptoInvestment.class);

        service.createCrypto("user@test.com", Map.of(
                "name", "Bitcoin", "symbol", "BTC",
                "amount", "0.5", "investAmount", "15000", "currency", "USD"));

        verify(cryptoRepo).save(cap.capture());
        assertThat(cap.getValue().getSymbol()).isEqualTo("BTC");
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("0.5");
        assertThat(cap.getValue().getId()).isNotBlank();
    }

    @Test
    void deleteCrypto_nonOwnerThrowsSecurityException() {
        CryptoInvestment c = crypto("c-1", "owner@test.com", "BTC");
        when(cryptoRepo.findById("c-1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.deleteCrypto("c-1", "attacker@test.com"))
                .isInstanceOf(SecurityException.class);
        verify(cryptoRepo, never()).delete(any());
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Test
    void listCards_returnsTypesAsList() {
        Card c = card("card-1", "user@test.com", "Credit,Debit");
        when(cardRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

        var result = service.listCards("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).types()).containsExactlyInAnyOrder("Credit", "Debit");
    }

    @Test
    void listCards_emptyTypes_returnsEmptyList() {
        Card c = card("card-1", "user@test.com", "");
        when(cardRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

        var result = service.listCards("user@test.com");

        assertThat(result.get(0).types()).isEmpty();
    }

    @Test
    void createCard_savesWithCommaJoinedTypes() {
        when(cardRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<Card> cap = ArgumentCaptor.forClass(Card.class);

        service.createCard("user@test.com", Map.of(
                "bank", "DBS", "countryRegion", "SG",
                "types", List.of("Credit", "ATM"),
                "cardName", "DBS Altitude", "network", "Visa",
                "expireDate", "2027-12"));

        verify(cardRepo).save(cap.capture());
        assertThat(cap.getValue().getBank()).isEqualTo("DBS");
        assertThat(cap.getValue().getTypes()).contains("Credit");
        assertThat(cap.getValue().getTypes()).contains("ATM");
        assertThat(cap.getValue().getId()).isNotBlank();
    }

    @Test
    void updateCard_nonOwnerThrowsSecurityException() {
        Card c = card("card-1", "owner@test.com", "Credit");
        when(cardRepo.findById("card-1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.updateCard("card-1", "attacker@test.com", Map.of()))
                .isInstanceOf(SecurityException.class);
        verify(cardRepo, never()).save(any());
    }

    @Test
    void deleteCard_ownerCanDelete() {
        Card c = card("card-1", "owner@test.com", "Debit");
        when(cardRepo.findById("card-1")).thenReturn(Optional.of(c));

        service.deleteCard("card-1", "owner@test.com");

        verify(cardRepo).delete(c);
    }

    @Test
    void ownerCheck_exactUuidMatch_doesNotThrow() {
        CashDeposit d = deposit("d-1", "owner-uuid", "USD", new BigDecimal("100"));
        when(depositRepo.findById("d-1")).thenReturn(Optional.of(d));

        service.deleteDeposit("d-1", "owner-uuid");

        verify(depositRepo).delete(d);
    }

    // ── refreshPrices ─────────────────────────────────────────────────────────

    @Test
    void refreshPrices_triggersStockAndCryptoRefresh() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");
        CryptoInvestment c = crypto("c-1", "user@test.com", "ETH");
        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

        service.refreshPrices("user@test.com");

        verify(priceService).refreshStockPrices(List.of("AAPL"));
        verify(priceService).refreshCryptoPrices(List.of("ETH"));
    }

    @Test
    void refreshPrices_noSymbols_noRefreshCalled() {
        when(stockRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of());
        when(cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of());

        service.refreshPrices("user@test.com");

        verify(priceService, never()).refreshStockPrices(any());
        verify(priceService, never()).refreshCryptoPrices(any());
    }

    // ── Missing update paths ───────────────────────────────────────────────────

    @Test
    void updateStock_ownerMatch_updatesAndSaves() {
        StockInvestment s = stock("s-1", "owner@test.com", "AAPL");
        when(stockRepo.findById("s-1")).thenReturn(Optional.of(s));
        when(stockRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        StockInvestment result = service.updateStock("s-1", "owner@test.com",
                Map.of("symbol", "TSLA", "name", "Tesla", "stockAmount", "5"));

        assertThat(result.getSymbol()).isEqualTo("TSLA");
        verify(stockRepo).save(s);
    }

    @Test
    void updateStock_notFound_throwsIllegalArgument() {
        when(stockRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStock("missing", "owner@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not found");
    }

    @Test
    void updateCrypto_ownerMatch_updatesAndSaves() {
        CryptoInvestment c = crypto("c-1", "owner@test.com", "BTC");
        when(cryptoRepo.findById("c-1")).thenReturn(Optional.of(c));
        when(cryptoRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CryptoInvestment result = service.updateCrypto("c-1", "owner@test.com",
                Map.of("symbol", "ETH", "amount", "2.0", "investAmount", "3000", "currency", "USD"));

        assertThat(result.getSymbol()).isEqualTo("ETH");
        verify(cryptoRepo).save(c);
    }

    @Test
    void updateCrypto_notFound_throwsIllegalArgument() {
        when(cryptoRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCrypto("missing", "owner@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateCrypto_nonOwner_throwsSecurityException() {
        CryptoInvestment c = crypto("c-1", "owner@test.com", "BTC");
        when(cryptoRepo.findById("c-1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.updateCrypto("c-1", "other@test.com", Map.of()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void deleteCrypto_notFound_doesNothing() {
        when(cryptoRepo.findById("ghost")).thenReturn(Optional.empty());

        service.deleteCrypto("ghost", "owner@test.com");

        verify(cryptoRepo, never()).delete(any());
    }

    @Test
    void updateCard_ownerMatch_updatesAndSaves() {
        Card c = card("card-1", "owner@test.com", "Credit");
        when(cardRepo.findById("card-1")).thenReturn(Optional.of(c));
        when(cardRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateCard("card-1", "owner@test.com",
                Map.of("cardName", "Gold Card", "network", "Mastercard",
                       "creditLimit", "50000", "creditLimitCurrency", "HKD",
                       "sharedCredit", true));

        verify(cardRepo).save(c);
        assertThat(c.getCardName()).isEqualTo("Gold Card");
    }

    @Test
    void updateCard_notFound_throwsIllegalArgument() {
        when(cardRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCard("missing", "owner@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteStock_notFound_doesNothing() {
        when(stockRepo.findById("ghost")).thenReturn(Optional.empty());

        service.deleteStock("ghost", "owner@test.com");

        verify(stockRepo, never()).delete(any());
    }

    @Test
    void deleteCard_notFound_doesNothing() {
        when(cardRepo.findById("ghost")).thenReturn(Optional.empty());

        service.deleteCard("ghost", "owner@test.com");

        verify(cardRepo, never()).delete(any());
    }

    // ── Salary Usage Records ───────────────────────────────────────────────────

    @Test
    void listSalary_returnsAllRecords() {
        SalaryUsageRecord r = salary("sal-1", "user@test.com");
        when(salaryRepo.findByOwnerUuidOrderByYearDescMonthDesc("user@test.com")).thenReturn(List.of(r));

        var result = service.listSalary("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("sal-1");
        assertThat(result.get(0).ownerUuid()).isEqualTo("user@test.com");
    }

    @Test
    void listSalary_noRecords_returnsEmpty() {
        when(salaryRepo.findByOwnerUuidOrderByYearDescMonthDesc("user@test.com")).thenReturn(List.of());

        assertThat(service.listSalary("user@test.com")).isEmpty();
    }

    @Test
    void createSalary_savesWithOwnerUuid() {
        when(salaryRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var body = new java.util.HashMap<String, Object>();
        body.put("year", 2025); body.put("month", 6); body.put("region", "HK");
        body.put("currency", "HKD"); body.put("salary", "50000"); body.put("bonus", "5000");
        body.put("retirementSavingEmployee", "2500"); body.put("retirementSavingEmployer", "2500");
        body.put("tax", "3000"); body.put("houseRent", "10000"); body.put("livingExpense", "5000");
        body.put("otherExpense", "2000"); body.put("totalExpense", "17000");

        SalaryUsageRecord result = service.createSalary("owner@test.com", body);

        assertThat(result.getOwnerUuid()).isEqualTo("owner@test.com");
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getYear()).isEqualTo(2025);
        assertThat(result.getCurrency()).isEqualTo("HKD");
        verify(salaryRepo).save(result);
    }

    @Test
    void createSalary_withoutTotalExpense_defaultsToZero() {
        when(salaryRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        var body = new java.util.HashMap<String, Object>();
        body.put("year", 2025); body.put("month", 1); body.put("region", "SG");
        body.put("currency", "SGD"); body.put("salary", "8000"); body.put("bonus", "0");
        body.put("retirementSavingEmployee", "800"); body.put("retirementSavingEmployer", "800");
        body.put("tax", "500"); body.put("houseRent", "2000"); body.put("livingExpense", "1500");
        body.put("otherExpense", "500");

        SalaryUsageRecord result = service.createSalary("owner@test.com", body);

        // totalExpense is only ever set when the caller explicitly provides it — same
        // as every other field here — so an omitted value stays at the entity default.
        assertThat(result.getTotalExpense()).isEqualByComparingTo("0");
    }

    @Test
    void updateSalary_omittingTotalExpense_preservesExistingValue() {
        SalaryUsageRecord r = salary("sal-1", "owner@test.com");
        // Deliberately not equal to houseRent+livingExpense+otherExpense (17000), so a
        // regression that recomputes totalExpense from those fields would be caught.
        r.setTotalExpense(new BigDecimal("99999"));
        when(salaryRepo.findById("sal-1")).thenReturn(Optional.of(r));
        when(salaryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Only updating an unrelated field — totalExpense must not be touched.
        SalaryUsageRecord result = service.updateSalary("sal-1", "owner@test.com", Map.of("region", "JP"));

        assertThat(result.getTotalExpense()).isEqualByComparingTo("99999");
        assertThat(result.getRegion()).isEqualTo("JP");
    }

    @Test
    void updateSalary_ownerMatch_updatesAndSaves() {
        SalaryUsageRecord r = salary("sal-1", "owner@test.com");
        when(salaryRepo.findById("sal-1")).thenReturn(Optional.of(r));
        when(salaryRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        var upd = new java.util.HashMap<String, Object>();
        upd.put("year", 2026); upd.put("month", 3); upd.put("region", "UK");
        upd.put("currency", "GBP"); upd.put("salary", "60000"); upd.put("bonus", "6000");
        upd.put("retirementSavingEmployee", "3000"); upd.put("retirementSavingEmployer", "3000");
        upd.put("tax", "4000"); upd.put("houseRent", "12000"); upd.put("livingExpense", "6000");
        upd.put("otherExpense", "2500"); upd.put("totalExpense", "20500");
        service.updateSalary("sal-1", "owner@test.com", upd);

        verify(salaryRepo).save(r);
        assertThat(r.getYear()).isEqualTo(2026);
        assertThat(r.getCurrency()).isEqualTo("GBP");
    }

    @Test
    void updateSalary_notFound_throwsIllegalArgument() {
        when(salaryRepo.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSalary("missing", "owner@test.com", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSalary_nonOwner_throwsSecurityException() {
        SalaryUsageRecord r = salary("sal-1", "owner@test.com");
        when(salaryRepo.findById("sal-1")).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.updateSalary("sal-1", "other@test.com", Map.of()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void deleteSalary_ownerCanDelete() {
        SalaryUsageRecord r = salary("sal-1", "owner@test.com");
        when(salaryRepo.findById("sal-1")).thenReturn(Optional.of(r));

        service.deleteSalary("sal-1", "owner@test.com");

        verify(salaryRepo).delete(r);
    }

    @Test
    void deleteSalary_notFound_doesNothing() {
        when(salaryRepo.findById("ghost")).thenReturn(Optional.empty());

        service.deleteSalary("ghost", "owner@test.com");

        verify(salaryRepo, never()).delete(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CashDeposit deposit(String id, String email, String currency, BigDecimal amount) {
        CashDeposit d = new CashDeposit();
        d.setId(id);
        d.setOwnerUuid(email);
        d.setCurrency(currency);
        d.setAmount(amount);
        return d;
    }

    private StockInvestment stock(String id, String email, String symbol) {
        StockInvestment s = new StockInvestment();
        s.setId(id);
        s.setOwnerUuid(email);
        s.setSymbol(symbol);
        s.setStockAmount(new BigDecimal("10"));
        s.setInvestAmount(new BigDecimal("1000"));
        s.setCurrency("USD");
        s.setFee(BigDecimal.ZERO);
        return s;
    }

    private CryptoInvestment crypto(String id, String email, String symbol) {
        CryptoInvestment c = new CryptoInvestment();
        c.setId(id);
        c.setOwnerUuid(email);
        c.setSymbol(symbol);
        c.setAmount(new BigDecimal("1"));
        c.setInvestAmount(new BigDecimal("1000"));
        c.setCurrency("USD");
        return c;
    }

    private Card card(String id, String email, String types) {
        Card c = new Card();
        c.setId(id);
        c.setOwnerUuid(email);
        c.setTypes(types);
        return c;
    }

    private SalaryUsageRecord salary(String id, String email) {
        SalaryUsageRecord r = new SalaryUsageRecord();
        r.setId(id);
        r.setOwnerUuid(email);
        r.setYear(2025);
        r.setMonth(6);
        r.setRegion("HK");
        r.setCurrency("HKD");
        r.setSalary(new BigDecimal("50000"));
        r.setBonus(new BigDecimal("5000"));
        r.setRetirementSavingEmployee(new BigDecimal("2500"));
        r.setRetirementSavingEmployer(new BigDecimal("2500"));
        r.setTax(new BigDecimal("3000"));
        r.setHouseRent(new BigDecimal("10000"));
        r.setLivingExpense(new BigDecimal("5000"));
        r.setOtherExpense(new BigDecimal("2000"));
        r.setTotalExpense(new BigDecimal("17000"));
        return r;
    }
}
