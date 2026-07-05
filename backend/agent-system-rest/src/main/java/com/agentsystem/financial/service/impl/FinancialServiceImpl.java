package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.FinancialService;
import com.agentsystem.financial.service.ExchangeRateService;
import com.agentsystem.financial.service.MarketPriceService;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialServiceImpl implements FinancialService {

    private final CashDepositRepository       depositRepo;
    private final StockInvestmentRepository   stockRepo;
    private final CryptoInvestmentRepository  cryptoRepo;
    private final CardRepository              cardRepo;
    private final SalaryUsageRecordRepository salaryRepo;
    private final ExchangeRateService         fxService;
    private final MarketPriceService          priceService;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<CashDepositDto> listDeposits(String ownerUuid, String defaultCurrency) {
        return depositRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid).stream()
                .map(d -> toDto(d, defaultCurrency))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CashDeposit createDeposit(String ownerUuid, Map<String, Object> body) {
        CashDeposit d = new CashDeposit();
        d.setId(UUID.randomUUID().toString());
        d.setOwnerUuid(ownerUuid);
        applyDepositFields(d, body);
        return depositRepo.save(d);
    }

    @Override
    @Transactional
    public CashDeposit updateDeposit(String id, String ownerUuid, Map<String, Object> body) {
        CashDeposit d = depositRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(d.getOwnerUuid(), ownerUuid);
        applyDepositFields(d, body);
        d.setUpdatedAt(Instant.now());
        return depositRepo.save(d);
    }

    @Override
    @Transactional
    public void deleteDeposit(String id, String ownerUuid) {
        depositRepo.findById(id).ifPresent(d -> {
            checkOwner(d.getOwnerUuid(), ownerUuid);
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
                d.getId(), d.getOwnerUuid(), d.getPlatform(), d.getPlatformType(),
                d.getCountryRegion(), d.getDepositType(), d.getCurrency(), d.getAmount(),
                bd(converted), toCurrency, d.getCreatedAt(), d.getUpdatedAt()
        );
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<StockInvestmentDto> listStocks(String ownerUuid, String defaultCurrency) {
        List<StockInvestment> stocks = stockRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid);

        // Auto-refresh prices if stale
        if (!stocks.isEmpty() && priceService.isStockStale()) {
            List<String> symbols = stocks.stream()
                    .map(StockInvestment::getSymbol).distinct().collect(Collectors.toList());
            priceService.refreshStockPrices(symbols);
        }

        return stocks.stream().map(s -> toDto(s, defaultCurrency)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public StockInvestment createStock(String ownerUuid, Map<String, Object> body) {
        StockInvestment s = new StockInvestment();
        s.setId(UUID.randomUUID().toString());
        s.setOwnerUuid(ownerUuid);
        applyStockFields(s, body);
        return stockRepo.save(s);
    }

    @Override
    @Transactional
    public StockInvestment updateStock(String id, String ownerUuid, Map<String, Object> body) {
        StockInvestment s = stockRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(s.getOwnerUuid(), ownerUuid);
        applyStockFields(s, body);
        s.setUpdatedAt(Instant.now());
        return stockRepo.save(s);
    }

    @Override
    @Transactional
    public void deleteStock(String id, String ownerUuid) {
        stockRepo.findById(id).ifPresent(s -> {
            checkOwner(s.getOwnerUuid(), ownerUuid);
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

        Double pnlPercent = null;
        if (convertedCurrentValue != null && convertedInvest > 0) {
            pnlPercent = Math.round((convertedCurrentValue.doubleValue() - convertedInvest) / convertedInvest * 10000.0) / 100.0;
        }

        return new StockInvestmentDto(
                s.getId(), s.getOwnerUuid(), s.getBroker(), s.getStockType(),
                s.getSymbol(), s.getName(), s.getStockAmount(), s.getInvestAmount(),
                s.getCurrency(), s.getFee(),
                currentPrice, priceCurrency, currentValue,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                pnlPercent, s.getCreatedAt(), s.getUpdatedAt()
        );
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<CryptoInvestmentDto> listCrypto(String ownerUuid, String defaultCurrency) {
        List<CryptoInvestment> list = cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid);

        if (!list.isEmpty() && priceService.isCryptoStale()) {
            List<String> symbols = list.stream()
                    .map(CryptoInvestment::getSymbol).distinct().collect(Collectors.toList());
            priceService.refreshCryptoPrices(symbols);
        }

        return list.stream().map(c -> toDto(c, defaultCurrency)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CryptoInvestment createCrypto(String ownerUuid, Map<String, Object> body) {
        CryptoInvestment c = new CryptoInvestment();
        c.setId(UUID.randomUUID().toString());
        c.setOwnerUuid(ownerUuid);
        applyCryptoFields(c, body);
        return cryptoRepo.save(c);
    }

    @Override
    @Transactional
    public CryptoInvestment updateCrypto(String id, String ownerUuid, Map<String, Object> body) {
        CryptoInvestment c = cryptoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(c.getOwnerUuid(), ownerUuid);
        applyCryptoFields(c, body);
        c.setUpdatedAt(Instant.now());
        return cryptoRepo.save(c);
    }

    @Override
    @Transactional
    public void deleteCrypto(String id, String ownerUuid) {
        cryptoRepo.findById(id).ifPresent(c -> {
            checkOwner(c.getOwnerUuid(), ownerUuid);
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

        Double pnlPercent = null;
        if (convertedCurrentValue != null && convertedInvest > 0) {
            pnlPercent = Math.round((convertedCurrentValue.doubleValue() - convertedInvest) / convertedInvest * 10000.0) / 100.0;
        }

        return new CryptoInvestmentDto(
                c.getId(), c.getOwnerUuid(), c.getName(), c.getSymbol(),
                c.getAmount(), c.getInvestAmount(), c.getCurrency(),
                currentPrice, currentValue,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                pnlPercent, c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<CardDto> listCards(String ownerUuid) {
        return cardRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Card createCard(String ownerUuid, Map<String, Object> body) {
        Card c = new Card();
        c.setId(UUID.randomUUID().toString());
        c.setOwnerUuid(ownerUuid);
        applyCardFields(c, body);
        return cardRepo.save(c);
    }

    @Override
    @Transactional
    public Card updateCard(String id, String ownerUuid, Map<String, Object> body) {
        Card c = cardRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(c.getOwnerUuid(), ownerUuid);
        applyCardFields(c, body);
        c.setUpdatedAt(Instant.now());
        return cardRepo.save(c);
    }

    @Override
    @Transactional
    public void deleteCard(String id, String ownerUuid) {
        cardRepo.findById(id).ifPresent(c -> {
            checkOwner(c.getOwnerUuid(), ownerUuid);
            cardRepo.delete(c);
        });
    }

    @SuppressWarnings("unchecked")
    private void applyCardFields(Card c, Map<String, Object> body) {
        if (body.containsKey("bank"))          c.setBank((String) body.get("bank"));
        if (body.containsKey("countryRegion")) c.setCountryRegion((String) body.get("countryRegion"));
        if (body.containsKey("types")) {
            List<String> types = (List<String>) body.get("types");
            c.setTypes(types == null ? "" : String.join(",", types));
        }
        if (body.containsKey("cardName"))    c.setCardName((String) body.get("cardName"));
        if (body.containsKey("network"))     c.setNetwork((String) body.get("network"));
        if (body.containsKey("expireDate"))  c.setExpireDate((String) body.get("expireDate"));
        if (body.containsKey("creditLimit")) {
            Object cl = body.get("creditLimit");
            c.setCreditLimit(cl != null ? new BigDecimal(cl.toString()) : null);
        }
        if (body.containsKey("creditLimitCurrency")) c.setCreditLimitCurrency((String) body.get("creditLimitCurrency"));
        if (body.containsKey("sharedCredit")) {
            Object sc = body.get("sharedCredit");
            c.setSharedCredit(sc instanceof Boolean ? (Boolean) sc : null);
        }
    }

    private CardDto toDto(Card c) {
        List<String> types = (c.getTypes() == null || c.getTypes().isBlank())
                ? List.of()
                : Arrays.asList(c.getTypes().split(","));
        return new CardDto(
                c.getId(), c.getOwnerUuid(), c.getBank(), c.getCountryRegion(), types,
                c.getCardName(), c.getNetwork(), c.getExpireDate(),
                c.getCreditLimit(), c.getCreditLimitCurrency(), c.getSharedCredit(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<SalaryUsageRecordDto> listSalary(String ownerUuid) {
        return salaryRepo.findByOwnerUuidOrderByYearDescMonthDesc(ownerUuid)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SalaryUsageRecord createSalary(String ownerUuid, Map<String, Object> body) {
        SalaryUsageRecord r = new SalaryUsageRecord();
        r.setId(UUID.randomUUID().toString());
        r.setOwnerUuid(ownerUuid);
        applySalaryFields(r, body);
        return salaryRepo.save(r);
    }

    @Override
    @Transactional
    public SalaryUsageRecord updateSalary(String id, String ownerUuid, Map<String, Object> body) {
        SalaryUsageRecord r = salaryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(r.getOwnerUuid(), ownerUuid);
        applySalaryFields(r, body);
        r.setUpdatedAt(Instant.now());
        return salaryRepo.save(r);
    }

    @Override
    @Transactional
    public void deleteSalary(String id, String ownerUuid) {
        salaryRepo.findById(id).ifPresent(r -> {
            checkOwner(r.getOwnerUuid(), ownerUuid);
            salaryRepo.delete(r);
        });
    }

    private void applySalaryFields(SalaryUsageRecord r, Map<String, Object> body) {
        if (body.containsKey("year"))          r.setYear(toInt(body.get("year")));
        if (body.containsKey("month"))         r.setMonth(toInt(body.get("month")));
        if (body.containsKey("region"))        r.setRegion((String) body.get("region"));
        if (body.containsKey("currency"))      r.setCurrency((String) body.get("currency"));
        if (body.containsKey("salary"))        r.setSalary(bd(body.get("salary")));
        if (body.containsKey("bonus"))          r.setBonus(bd(body.get("bonus")));
        if (body.containsKey("retirementSavingEmployee")) r.setRetirementSavingEmployee(bd(body.get("retirementSavingEmployee")));
        if (body.containsKey("retirementSavingEmployer")) r.setRetirementSavingEmployer(bd(body.get("retirementSavingEmployer")));
        if (body.containsKey("tax"))           r.setTax(bd(body.get("tax")));
        if (body.containsKey("houseRent"))     r.setHouseRent(bd(body.get("houseRent")));
        if (body.containsKey("livingExpense")) r.setLivingExpense(bd(body.get("livingExpense")));
        if (body.containsKey("otherExpense"))  r.setOtherExpense(bd(body.get("otherExpense")));
        if (body.containsKey("totalExpense"))  r.setTotalExpense(bd(body.get("totalExpense")));
    }

    private SalaryUsageRecordDto toDto(SalaryUsageRecord r) {
        return new SalaryUsageRecordDto(
                r.getId(), r.getOwnerUuid(), r.getYear(), r.getMonth(),
                r.getRegion(), r.getCurrency(), r.getSalary(), r.getBonus(),
                r.getRetirementSavingEmployee(), r.getRetirementSavingEmployer(), r.getTax(),
                r.getHouseRent(), r.getLivingExpense(), r.getOtherExpense(), r.getTotalExpense(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    // ── Price refresh (on-demand) ─────────────────────────────────────────────

    @Override
    public void refreshPrices(String ownerUuid) {
        List<String> stockSymbols = stockRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid)
                .stream().map(StockInvestment::getSymbol).distinct().collect(Collectors.toList());
        List<String> cryptoSymbols = cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid)
                .stream().map(CryptoInvestment::getSymbol).distinct().collect(Collectors.toList());

        if (!stockSymbols.isEmpty())  priceService.refreshStockPrices(stockSymbols);
        if (!cryptoSymbols.isEmpty()) priceService.refreshCryptoPrices(cryptoSymbols);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Object v) {
        return new BigDecimal(v.toString()).setScale(4, RoundingMode.HALF_UP);
    }

    private static int toInt(Object v) {
        return Integer.parseInt(v.toString());
    }

    private void checkOwner(String recordUuid, String callerUuid) {
        if (recordUuid != null && callerUuid != null
                && !recordUuid.equals(callerUuid)) {
            throw new SecurityException("Only the owner can modify this record.");
        }
    }
}
