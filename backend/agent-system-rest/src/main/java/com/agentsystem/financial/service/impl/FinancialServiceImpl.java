package com.agentsystem.financial.service.impl;

import com.agentsystem.financial.service.FinancialService;
import com.agentsystem.financial.service.CexFuturesPriceService;
import com.agentsystem.financial.service.ExchangeRateService;
import com.agentsystem.financial.service.HyperliquidPositionService;
import com.agentsystem.financial.service.MarketPriceService;

import com.agentsystem.financial.dto.CardDto;
import com.agentsystem.financial.dto.CashDepositDto;
import com.agentsystem.financial.dto.CryptoInvestmentDto;
import com.agentsystem.financial.dto.FutureInvestmentDto;
import com.agentsystem.financial.dto.SalaryUsageRecordDto;
import com.agentsystem.financial.dto.StockInvestmentDto;
import com.agentsystem.financial.entity.Card;
import com.agentsystem.financial.entity.CashDeposit;
import com.agentsystem.financial.entity.CryptoInvestment;
import com.agentsystem.financial.entity.FutureInvestment;
import com.agentsystem.financial.entity.SalaryUsageRecord;
import com.agentsystem.financial.entity.StockInvestment;
import com.agentsystem.financial.repository.CardRepository;
import com.agentsystem.financial.repository.CashDepositRepository;
import com.agentsystem.financial.repository.CryptoInvestmentRepository;
import com.agentsystem.financial.repository.FutureInvestmentRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialServiceImpl implements FinancialService {

    private final CashDepositRepository       depositRepo;
    private final StockInvestmentRepository   stockRepo;
    private final CryptoInvestmentRepository  cryptoRepo;
    private final FutureInvestmentRepository  futureRepo;
    private final CardRepository              cardRepo;
    private final SalaryUsageRecordRepository salaryRepo;
    private final ExchangeRateService         fxService;
    private final MarketPriceService          priceService;
    private final HyperliquidPositionService  hyperliquidService;
    private final CexFuturesPriceService      cexPriceService;

    private static final Set<String> CEX_EXCHANGES = Set.of("BINANCE", "OKX", "KRAKEN");

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
        String  logoUrl        = priceService.getStockLogo(sym).orElse(null);

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
                currentPrice, priceCurrency, logoUrl, currentValue,
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
        String     logoUrl                = priceService.getCryptoLogo(sym).orElse(null);
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
                currentPrice, logoUrl, currentValue,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                pnlPercent, c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @Override
    public List<FutureInvestmentDto> listFutures(String ownerUuid, String defaultCurrency) {
        List<FutureInvestment> rows = futureRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid);

        List<FutureInvestment> manual = rows.stream()
                .filter(f -> !"CRYPTO_DEX".equals(f.getExchangeKind()))
                .collect(Collectors.toList());
        List<FutureInvestment> dexConnections = rows.stream()
                .filter(f -> "CRYPTO_DEX".equals(f.getExchangeKind()))
                .collect(Collectors.toList());

        List<CexFuturesPriceService.ExchangeSymbol> cexPairs = manual.stream()
                .filter(f -> "CRYPTO_CEX".equals(f.getExchangeKind()))
                .map(f -> new CexFuturesPriceService.ExchangeSymbol(f.getExchange(), f.getSymbol()))
                .distinct().collect(Collectors.toList());
        List<String> securitySymbols = manual.stream()
                .filter(f -> "SECURITY".equals(f.getExchangeKind()))
                .map(FutureInvestment::getSymbol).distinct().collect(Collectors.toList());

        if (!cexPairs.isEmpty() && cexPriceService.isStale())          cexPriceService.refreshPrices(cexPairs);
        if (!securitySymbols.isEmpty() && priceService.isStockStale()) priceService.refreshStockPrices(securitySymbols);

        List<FutureInvestmentDto> result = manual.stream()
                .map(f -> toManualFutureDto(f, defaultCurrency))
                .collect(Collectors.toList());

        for (FutureInvestment conn : dexConnections) {
            List<HyperliquidPositionService.Position> positions = hyperliquidService.fetchPositions(conn.getConnectionAddress());
            if (positions.isEmpty()) {
                // No open positions right now — still surface the tracked address itself so it stays manageable/deletable.
                result.add(toHyperliquidPlaceholderDto(conn, defaultCurrency));
            } else {
                for (HyperliquidPositionService.Position pos : positions) {
                    result.add(toHyperliquidFutureDto(conn, pos, defaultCurrency));
                }
            }
        }
        return result;
    }

    @Override
    @Transactional
    public FutureInvestment createFuture(String ownerUuid, Map<String, Object> body) {
        FutureInvestment f = new FutureInvestment();
        f.setId(UUID.randomUUID().toString());
        f.setOwnerUuid(ownerUuid);
        applyFutureFields(f, body);
        return futureRepo.save(f);
    }

    @Override
    @Transactional
    public FutureInvestment updateFuture(String id, String ownerUuid, Map<String, Object> body) {
        FutureInvestment f = futureRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Not found"));
        checkOwner(f.getOwnerUuid(), ownerUuid);
        applyFutureFields(f, body);
        f.setUpdatedAt(Instant.now());
        return futureRepo.save(f);
    }

    @Override
    @Transactional
    public void deleteFuture(String id, String ownerUuid) {
        futureRepo.findById(id).ifPresent(f -> {
            checkOwner(f.getOwnerUuid(), ownerUuid);
            futureRepo.delete(f);
        });
    }

    private void applyFutureFields(FutureInvestment f, Map<String, Object> body) {
        String kind = body.containsKey("exchangeKind") ? (String) body.get("exchangeKind") : f.getExchangeKind();
        if (kind == null) throw new IllegalArgumentException("exchangeKind is required");
        f.setExchangeKind(kind);

        switch (kind) {
            case "CRYPTO_DEX" -> {
                f.setExchange("HYPERLIQUID");
                String address = body.containsKey("connectionAddress") ? (String) body.get("connectionAddress") : f.getConnectionAddress();
                if (address == null || address.isBlank()) {
                    throw new IllegalArgumentException("connectionAddress is required for CRYPTO_DEX");
                }
                f.setConnectionAddress(address);
                f.setSymbol(null);
                f.setSide(null);
                f.setQuantity(null);
                f.setEntryPrice(null);
                f.setLeverage(null);
            }
            case "SECURITY" -> {
                f.setExchange("IBKR");
                applyManualFutureFields(f, body);
            }
            case "CRYPTO_CEX" -> {
                String exchange = body.containsKey("exchange") ? (String) body.get("exchange") : f.getExchange();
                if (exchange == null || !CEX_EXCHANGES.contains(exchange.toUpperCase())) {
                    throw new IllegalArgumentException("exchange must be one of " + CEX_EXCHANGES + " for CRYPTO_CEX");
                }
                f.setExchange(exchange.toUpperCase());
                applyManualFutureFields(f, body);
            }
            default -> throw new IllegalArgumentException("Unknown exchangeKind: " + kind);
        }
    }

    private void applyManualFutureFields(FutureInvestment f, Map<String, Object> body) {
        if (body.containsKey("symbol"))     f.setSymbol(((String) body.get("symbol")).toUpperCase());
        if (body.containsKey("side"))       f.setSide((String) body.get("side"));
        if (body.containsKey("quantity"))   f.setQuantity(new BigDecimal(body.get("quantity").toString()));
        if (body.containsKey("entryPrice")) f.setEntryPrice(new BigDecimal(body.get("entryPrice").toString()));
        if (body.containsKey("leverage")) {
            Object lev = body.get("leverage");
            f.setLeverage(lev != null ? new BigDecimal(lev.toString()) : null);
        }
        if (body.containsKey("currency"))   f.setCurrency((String) body.get("currency"));
        f.setConnectionAddress(null);

        if (f.getSymbol() == null || f.getSymbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (f.getSide() == null || (!f.getSide().equals("LONG") && !f.getSide().equals("SHORT"))) {
            throw new IllegalArgumentException("side must be LONG or SHORT");
        }
        if (f.getQuantity() == null)   throw new IllegalArgumentException("quantity is required");
        if (f.getEntryPrice() == null) throw new IllegalArgumentException("entryPrice is required");
    }

    private FutureInvestmentDto toManualFutureDto(FutureInvestment f, String toCurrency) {
        String sym = f.getSymbol().toUpperCase();
        boolean isCrypto = "CRYPTO_CEX".equals(f.getExchangeKind());

        // CEX futures use each exchange's own native instrument id (e.g. Binance "BTCUSDT",
        // OKX "BTC-USDT-SWAP") and price — never the shared Hyperliquid spot-mids cache.
        Double currentPrice = isCrypto
                ? cexPriceService.getPrice(f.getExchange(), sym).orElse(null)
                : priceService.getStockPrice(sym).orElse(null);

        BigDecimal currentValue          = null;
        BigDecimal convertedCurrentValue = null;
        Double     pnlPercent            = null;

        // Margin (actual capital deployed) is what should count toward portfolio totals — not the
        // full leveraged notional. Without live per-position margin data for manual entries, the best
        // available approximation is notional / leverage (falls back to the full notional when
        // leverage is unset, i.e. 1x).
        BigDecimal notional = f.getEntryPrice().multiply(f.getQuantity());
        BigDecimal margin = (f.getLeverage() != null && f.getLeverage().signum() > 0)
                ? notional.divide(f.getLeverage(), 8, RoundingMode.HALF_UP)
                : notional;

        double convertedInvest = fxService.convert(margin.doubleValue(), f.getCurrency(), toCurrency);

        if (currentPrice != null) {
            currentValue = bd(currentPrice * f.getQuantity().doubleValue());
            double pnlPerUnit = "SHORT".equals(f.getSide())
                    ? f.getEntryPrice().doubleValue() - currentPrice
                    : currentPrice - f.getEntryPrice().doubleValue();
            double pnlNative = pnlPerUnit * f.getQuantity().doubleValue();
            double convertedPnl = fxService.convert(pnlNative, f.getCurrency(), toCurrency);
            convertedCurrentValue = bd(convertedInvest + convertedPnl);
            if (convertedInvest > 0) {
                pnlPercent = Math.round(convertedPnl / convertedInvest * 10000.0) / 100.0;
            }
        }

        return new FutureInvestmentDto(
                f.getId(), f.getOwnerUuid(), f.getExchangeKind(), f.getExchange(),
                f.getSymbol(), f.getSide(), f.getQuantity(), f.getEntryPrice(), f.getLeverage(),
                f.getCurrency(), f.getConnectionAddress(),
                currentPrice, currentValue, bd(margin), null, null,
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                pnlPercent, "MANUAL", null, null,
                f.getCreatedAt(), f.getUpdatedAt()
        );
    }

    private FutureInvestmentDto toHyperliquidFutureDto(
            FutureInvestment conn, HyperliquidPositionService.Position pos, String toCurrency) {
        Double currentPrice = pos.markPrice() != null ? pos.markPrice().doubleValue() : null;
        BigDecimal currentValue = currentPrice != null ? bd(currentPrice * pos.size().doubleValue()) : null;

        // Portfolio totals must be based on actual margin (capital at risk), not the full leveraged
        // notional — otherwise a leveraged position inflates the account's total value by its leverage
        // factor. Fall back to notional/leverage, then full notional, if margin wasn't parseable.
        BigDecimal notional = pos.entryPrice() != null ? pos.entryPrice().multiply(pos.size()) : BigDecimal.ZERO;
        BigDecimal margin = pos.margin() != null ? pos.margin()
                : (pos.leverage() != null && pos.leverage().signum() > 0)
                        ? notional.divide(pos.leverage(), 8, RoundingMode.HALF_UP)
                        : notional;

        double convertedInvest = fxService.convert(margin.doubleValue(), "USD", toCurrency);

        BigDecimal convertedCurrentValue = null;
        Double     pnlPercent            = null;
        if (pos.unrealizedPnl() != null) {
            double convertedPnl = fxService.convert(pos.unrealizedPnl().doubleValue(), "USD", toCurrency);
            convertedCurrentValue = bd(convertedInvest + convertedPnl);

            // Hyperliquid's displayed PNL% (ROE) is computed against margin at entry (fixed), not the
            // live `margin` field above (which drifts over time, e.g. from funding settlement) — so it
            // must come straight from their `returnOnEquity`, not convertedPnl / convertedInvest, or the
            // percentage shown here would silently diverge from what the exchange itself shows.
            if (pos.returnOnEquity() != null) {
                pnlPercent = Math.round(pos.returnOnEquity().doubleValue() * 10000.0) / 100.0;
            } else if (convertedInvest > 0) {
                pnlPercent = Math.round(convertedPnl / convertedInvest * 10000.0) / 100.0;
            }
        }

        return new FutureInvestmentDto(
                conn.getId() + ":" + pos.coin(), conn.getOwnerUuid(), "CRYPTO_DEX", "HYPERLIQUID",
                pos.coin(), pos.side(), pos.size(), pos.entryPrice(), pos.leverage(),
                "USD", conn.getConnectionAddress(),
                currentPrice, currentValue, bd(margin), pos.liquidationPrice(), pos.fundingSinceOpen(),
                bd(convertedInvest), convertedCurrentValue, toCurrency,
                pnlPercent, "HYPERLIQUID", conn.getId(),
                (pos.dex() == null || pos.dex().isEmpty()) ? null : pos.dex(),
                conn.getCreatedAt(), conn.getUpdatedAt()
        );
    }

    /** A tracked DEX address with no open positions right now — still shown so it can be removed. */
    private FutureInvestmentDto toHyperliquidPlaceholderDto(FutureInvestment conn, String toCurrency) {
        return new FutureInvestmentDto(
                conn.getId(), conn.getOwnerUuid(), "CRYPTO_DEX", "HYPERLIQUID",
                null, null, null, null, null,
                "USD", conn.getConnectionAddress(),
                null, null, null, null, null,
                BigDecimal.ZERO, null, toCurrency,
                null, "HYPERLIQUID", conn.getId(), null,
                conn.getCreatedAt(), conn.getUpdatedAt()
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
        List<FutureInvestment> futures = futureRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid);

        List<String> stockSymbols = Stream.concat(
                        stockRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid).stream().map(StockInvestment::getSymbol),
                        futures.stream().filter(f -> "SECURITY".equals(f.getExchangeKind())).map(FutureInvestment::getSymbol))
                .distinct().collect(Collectors.toList());
        List<String> cryptoSymbols = cryptoRepo.findByOwnerUuidOrderByCreatedAtDesc(ownerUuid)
                .stream().map(CryptoInvestment::getSymbol).distinct().collect(Collectors.toList());
        List<CexFuturesPriceService.ExchangeSymbol> cexPairs = futures.stream()
                .filter(f -> "CRYPTO_CEX".equals(f.getExchangeKind()))
                .map(f -> new CexFuturesPriceService.ExchangeSymbol(f.getExchange(), f.getSymbol()))
                .distinct().collect(Collectors.toList());

        if (!stockSymbols.isEmpty())  priceService.refreshStockPrices(stockSymbols);
        if (!cryptoSymbols.isEmpty()) priceService.refreshCryptoPrices(cryptoSymbols);
        if (!cexPairs.isEmpty())      cexPriceService.refreshPrices(cexPairs);
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
