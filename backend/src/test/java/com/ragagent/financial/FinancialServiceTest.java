package com.ragagent.financial;

import com.ragagent.financial.entity.Card;
import com.ragagent.financial.entity.CashDeposit;
import com.ragagent.financial.entity.CryptoInvestment;
import com.ragagent.financial.entity.StockInvestment;
import com.ragagent.financial.repository.CardRepository;
import com.ragagent.financial.repository.CashDepositRepository;
import com.ragagent.financial.repository.CryptoInvestmentRepository;
import com.ragagent.financial.repository.StockInvestmentRepository;
import com.ragagent.financial.service.ExchangeRateService;
import com.ragagent.financial.service.MarketPriceService;
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

    @Mock CashDepositRepository      depositRepo;
    @Mock StockInvestmentRepository  stockRepo;
    @Mock CryptoInvestmentRepository cryptoRepo;
    @Mock CardRepository             cardRepo;
    @Mock ExchangeRateService        fxService;
    @Mock MarketPriceService         priceService;

    @InjectMocks FinancialService service;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Test
    void listDeposits_returnsDtosWithConvertedAmount() {
        CashDeposit d = deposit("d-1", "user@test.com", "USD", new BigDecimal("1000"));
        when(depositRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(d));
        when(fxService.convert(1000.0, "USD", "HKD")).thenReturn(7800.0);

        var result = service.listDeposits("user@test.com", "HKD");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).convertedAmount()).isEqualByComparingTo("7800.0000");
        assertThat(result.get(0).convertedCurrency()).isEqualTo("HKD");
    }

    @Test
    void createDeposit_savesWithOwnerEmailAndUuid() {
        when(depositRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<CashDeposit> cap = ArgumentCaptor.forClass(CashDeposit.class);

        service.createDeposit("owner@test.com", Map.of(
                "platform", "HSBC", "platformType", "Bank", "currency", "HKD",
                "amount", "5000", "depositType", "FIXED", "countryRegion", "HK"));

        verify(depositRepo).save(cap.capture());
        assertThat(cap.getValue().getOwnerEmail()).isEqualTo("owner@test.com");
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
        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
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
        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
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

        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
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

        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(priceService.isStockStale()).thenReturn(false);
        when(priceService.getStockPrice("UNKNOWN")).thenReturn(Optional.empty());
        when(priceService.getStockCurrency("UNKNOWN")).thenReturn(Optional.empty());
        when(fxService.convert(500.0, "USD", "USD")).thenReturn(500.0);

        var result = service.listStocks("user@test.com", "USD");

        assertThat(result.get(0).pnlPercent()).isNull();
        assertThat(result.get(0).currentValue()).isNull();
    }

    @Test
    void createStock_savesWithOwnerEmailAndUuid() {
        when(stockRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArgumentCaptor<StockInvestment> cap = ArgumentCaptor.forClass(StockInvestment.class);

        service.createStock("user@test.com", Map.of(
                "broker", "Futu", "stockType", "US_STOCK",
                "symbol", "NVDA", "name", "NVIDIA",
                "stockAmount", "5", "investAmount", "2000",
                "currency", "USD", "fee", "1.5"));

        verify(stockRepo).save(cap.capture());
        assertThat(cap.getValue().getOwnerEmail()).isEqualTo("user@test.com");
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
        when(cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
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

        when(cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));
        when(priceService.isCryptoStale()).thenReturn(false);
        when(priceService.getCryptoPrice("ETH")).thenReturn(Optional.of(2500.0));
        when(fxService.convert(5000.0, "USD", "USD")).thenReturn(5000.0);
        when(fxService.convert(4000.0, "USD", "USD")).thenReturn(4000.0);

        var result = service.listCrypto("user@test.com", "USD");

        assertThat(result.get(0).pnlPercent()).isEqualTo(25.0);
        assertThat(result.get(0).currentValue()).isEqualByComparingTo("5000.0000");
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
        when(cardRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

        var result = service.listCards("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).types()).containsExactlyInAnyOrder("Credit", "Debit");
    }

    @Test
    void listCards_emptyTypes_returnsEmptyList() {
        Card c = card("card-1", "user@test.com", "");
        when(cardRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

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
    void ownerCheck_caseInsensitiveMatch_doesNotThrow() {
        CashDeposit d = deposit("d-1", "Owner@Test.COM", "USD", new BigDecimal("100"));
        when(depositRepo.findById("d-1")).thenReturn(Optional.of(d));

        service.deleteDeposit("d-1", "owner@test.com");

        verify(depositRepo).delete(d);
    }

    // ── refreshPrices ─────────────────────────────────────────────────────────

    @Test
    void refreshPrices_triggersStockAndCryptoRefresh() {
        StockInvestment s = stock("s-1", "user@test.com", "AAPL");
        CryptoInvestment c = crypto("c-1", "user@test.com", "ETH");
        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(s));
        when(cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of(c));

        service.refreshPrices("user@test.com");

        verify(priceService).refreshStockPrices(List.of("AAPL"));
        verify(priceService).refreshCryptoPrices(List.of("ETH"));
    }

    @Test
    void refreshPrices_noSymbols_noRefreshCalled() {
        when(stockRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of());
        when(cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc("user@test.com")).thenReturn(List.of());

        service.refreshPrices("user@test.com");

        verify(priceService, never()).refreshStockPrices(any());
        verify(priceService, never()).refreshCryptoPrices(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CashDeposit deposit(String id, String email, String currency, BigDecimal amount) {
        CashDeposit d = new CashDeposit();
        d.setId(id);
        d.setOwnerEmail(email);
        d.setCurrency(currency);
        d.setAmount(amount);
        return d;
    }

    private StockInvestment stock(String id, String email, String symbol) {
        StockInvestment s = new StockInvestment();
        s.setId(id);
        s.setOwnerEmail(email);
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
        c.setOwnerEmail(email);
        c.setSymbol(symbol);
        c.setAmount(new BigDecimal("1"));
        c.setInvestAmount(new BigDecimal("1000"));
        c.setCurrency("USD");
        return c;
    }

    private Card card(String id, String email, String types) {
        Card c = new Card();
        c.setId(id);
        c.setOwnerEmail(email);
        c.setTypes(types);
        return c;
    }
}
