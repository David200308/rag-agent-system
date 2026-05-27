package com.ragagent.financial;

import com.ragagent.financial.dto.CashDepositDto;
import com.ragagent.financial.dto.CryptoInvestmentDto;
import com.ragagent.financial.dto.StockInvestmentDto;
import com.ragagent.financial.entity.CashDeposit;
import com.ragagent.financial.entity.CryptoInvestment;
import com.ragagent.financial.entity.StockInvestment;
import com.ragagent.financial.repository.CashDepositRepository;
import com.ragagent.financial.repository.CryptoInvestmentRepository;
import com.ragagent.financial.repository.StockInvestmentRepository;
import com.ragagent.financial.service.ExchangeRateService;
import com.ragagent.financial.service.MarketPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final CashDepositRepository      depositRepo;
    private final StockInvestmentRepository  stockRepo;
    private final CryptoInvestmentRepository cryptoRepo;
    private final ExchangeRateService        fxService;
    private final MarketPriceService         priceService;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CashDepositDto> listDeposits(String ownerEmail, String defaultCurrency) {
        return depositRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail).stream()
                .map(d -> toDto(d, defaultCurrency))
                .collect(Collectors.toList());
    }

    @Transactional
    public CashDeposit createDeposit(String ownerEmail, Map<String, Object> body) {
        CashDeposit d = new CashDeposit();
        d.setId(UUID.randomUUID().toString());
        d.setOwnerEmail(ownerEmail);
        applyDepositFields(d, body);
        return depositRepo.save(d);
    }

    @Transactional
    public CashDeposit updateDeposit(String id, String ownerEmail, Map<String, Object> body) {
        CashDeposit d = depositRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(d.getOwnerEmail(), ownerEmail);
        applyDepositFields(d, body);
        d.setUpdatedAt(Instant.now());
        return depositRepo.save(d);
    }

    @Transactional
    public void deleteDeposit(String id, String ownerEmail) {
        depositRepo.findById(id).ifPresent(d -> {
            checkOwner(d.getOwnerEmail(), ownerEmail);
            depositRepo.delete(d);
        });
    }

    private void applyDepositFields(CashDeposit d, Map<String, Object> body) {
        if (body.containsKey("platform"))      d.setPlatform((String) body.get("platform"));
        if (body.containsKey("platformType"))  d.setPlatformType((String) body.get("platformType"));
        if (body.containsKey("countryRegion")) d.setCountryRegion((String) body.get("countryRegion"));
        if (body.containsKey("depositType"))   d.setDepositType((String) body.get("depositType"));
        if (body.containsKey("currency"))      d.setCurrency((String) body.get("currency"));
        if (body.containsKey("amount"))        d.setAmount(new BigDecimal(body.get("amount").toString()));
    }

    private CashDepositDto toDto(CashDeposit d, String toCurrency) {
        double converted = fxService.convert(d.getAmount().doubleValue(), d.getCurrency(), toCurrency);
        return new CashDepositDto(
                d.getId(), d.getOwnerEmail(), d.getPlatform(), d.getPlatformType(),
                d.getCountryRegion(), d.getDepositType(), d.getCurrency(), d.getAmount(),
                bd(converted), toCurrency, d.getCreatedAt(), d.getUpdatedAt()
        );
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockInvestmentDto> listStocks(String ownerEmail, String defaultCurrency) {
        List<StockInvestment> stocks = stockRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);

        // Auto-refresh prices if stale
        if (!stocks.isEmpty() && priceService.isStockStale()) {
            List<String> symbols = stocks.stream()
                    .map(StockInvestment::getSymbol).distinct().collect(Collectors.toList());
            priceService.refreshStockPrices(symbols);
        }

        return stocks.stream().map(s -> toDto(s, defaultCurrency)).collect(Collectors.toList());
    }

    @Transactional
    public StockInvestment createStock(String ownerEmail, Map<String, Object> body) {
        StockInvestment s = new StockInvestment();
        s.setId(UUID.randomUUID().toString());
        s.setOwnerEmail(ownerEmail);
        applyStockFields(s, body);
        return stockRepo.save(s);
    }

    @Transactional
    public StockInvestment updateStock(String id, String ownerEmail, Map<String, Object> body) {
        StockInvestment s = stockRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(s.getOwnerEmail(), ownerEmail);
        applyStockFields(s, body);
        s.setUpdatedAt(Instant.now());
        return stockRepo.save(s);
    }

    @Transactional
    public void deleteStock(String id, String ownerEmail) {
        stockRepo.findById(id).ifPresent(s -> {
            checkOwner(s.getOwnerEmail(), ownerEmail);
            stockRepo.delete(s);
        });
    }

    private void applyStockFields(StockInvestment s, Map<String, Object> body) {
        if (body.containsKey("broker"))       s.setBroker((String) body.get("broker"));
        if (body.containsKey("stockType"))    s.setStockType((String) body.get("stockType"));
        if (body.containsKey("symbol"))       s.setSymbol((String) body.get("symbol"));
        if (body.containsKey("name"))         s.setName((String) body.get("name"));
        if (body.containsKey("stockAmount"))  s.setStockAmount(new BigDecimal(body.get("stockAmount").toString()));
        if (body.containsKey("investAmount")) s.setInvestAmount(new BigDecimal(body.get("investAmount").toString()));
        if (body.containsKey("currency"))     s.setCurrency((String) body.get("currency"));
        if (body.containsKey("fee"))          s.setFee(new BigDecimal(body.get("fee").toString()));
    }

    private StockInvestmentDto toDto(StockInvestment s, String toCurrency) {
        String sym = s.getSymbol().toUpperCase();

        Double  currentPrice   = priceService.getStockPrice(sym).orElse(null);
        String  priceCurrency  = priceService.getStockCurrency(sym).orElse(s.getCurrency());

        BigDecimal currentValue             = null;
        BigDecimal convertedCurrentValue    = null;

        if (currentPrice != null) {
            currentValue = bd(currentPrice * s.getStockAmount().doubleValue());
            double convertedCv = fxService.convert(currentValue.doubleValue(), priceCurrency, toCurrency);
            convertedCurrentValue = bd(convertedCv);
        }

        double convertedInvest = fxService.convert(s.getInvestAmount().doubleValue(), s.getCurrency(), toCurrency);

        return new StockInvestmentDto(
                s.getId(), s.getOwnerEmail(), s.getBroker(), s.getStockType(),
                s.getSymbol(), s.getName(), s.getStockAmount(), s.getInvestAmount(),
                s.getCurrency(), s.getFee(),
                currentPrice, priceCurrency, currentValue,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                s.getCreatedAt(), s.getUpdatedAt()
        );
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CryptoInvestmentDto> listCrypto(String ownerEmail, String defaultCurrency) {
        List<CryptoInvestment> list = cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);

        if (!list.isEmpty() && priceService.isCryptoStale()) {
            List<String> symbols = list.stream()
                    .map(CryptoInvestment::getSymbol).distinct().collect(Collectors.toList());
            priceService.refreshCryptoPrices(symbols);
        }

        return list.stream().map(c -> toDto(c, defaultCurrency)).collect(Collectors.toList());
    }

    @Transactional
    public CryptoInvestment createCrypto(String ownerEmail, Map<String, Object> body) {
        CryptoInvestment c = new CryptoInvestment();
        c.setId(UUID.randomUUID().toString());
        c.setOwnerEmail(ownerEmail);
        applyCryptoFields(c, body);
        return cryptoRepo.save(c);
    }

    @Transactional
    public CryptoInvestment updateCrypto(String id, String ownerEmail, Map<String, Object> body) {
        CryptoInvestment c = cryptoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(c.getOwnerEmail(), ownerEmail);
        applyCryptoFields(c, body);
        c.setUpdatedAt(Instant.now());
        return cryptoRepo.save(c);
    }

    @Transactional
    public void deleteCrypto(String id, String ownerEmail) {
        cryptoRepo.findById(id).ifPresent(c -> {
            checkOwner(c.getOwnerEmail(), ownerEmail);
            cryptoRepo.delete(c);
        });
    }

    private void applyCryptoFields(CryptoInvestment c, Map<String, Object> body) {
        if (body.containsKey("name"))         c.setName((String) body.get("name"));
        if (body.containsKey("symbol"))       c.setSymbol((String) body.get("symbol"));
        if (body.containsKey("amount"))       c.setAmount(new BigDecimal(body.get("amount").toString()));
        if (body.containsKey("investAmount")) c.setInvestAmount(new BigDecimal(body.get("investAmount").toString()));
        if (body.containsKey("currency"))     c.setCurrency((String) body.get("currency"));
    }

    private CryptoInvestmentDto toDto(CryptoInvestment c, String toCurrency) {
        String sym = c.getSymbol().toUpperCase();

        Double     currentPrice           = priceService.getCryptoPrice(sym).orElse(null);
        BigDecimal currentValue           = null;
        BigDecimal convertedCurrentValue  = null;

        if (currentPrice != null) {
            currentValue = bd(currentPrice * c.getAmount().doubleValue());
            // Binance prices are in USDT ≈ USD
            double convertedCv = fxService.convert(currentValue.doubleValue(), "USD", toCurrency);
            convertedCurrentValue = bd(convertedCv);
        }

        double convertedInvest = fxService.convert(c.getInvestAmount().doubleValue(), c.getCurrency(), toCurrency);

        return new CryptoInvestmentDto(
                c.getId(), c.getOwnerEmail(), c.getName(), c.getSymbol(),
                c.getAmount(), c.getInvestAmount(), c.getCurrency(),
                currentPrice, currentValue,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    // ── Price refresh (on-demand) ─────────────────────────────────────────────

    public void refreshPrices(String ownerEmail) {
        List<String> stockSymbols = stockRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream().map(StockInvestment::getSymbol).distinct().collect(Collectors.toList());
        List<String> cryptoSymbols = cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream().map(CryptoInvestment::getSymbol).distinct().collect(Collectors.toList());

        if (!stockSymbols.isEmpty())  priceService.refreshStockPrices(stockSymbols);
        if (!cryptoSymbols.isEmpty()) priceService.refreshCryptoPrices(cryptoSymbols);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private void checkOwner(String recordEmail, String callerEmail) {
        if (recordEmail != null && callerEmail != null
                && !recordEmail.equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can modify this record.");
        }
    }
}
