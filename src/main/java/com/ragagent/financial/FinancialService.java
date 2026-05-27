package com.ragagent.financial;

import com.ragagent.financial.entity.CashDeposit;
import com.ragagent.financial.entity.CryptoInvestment;
import com.ragagent.financial.entity.StockInvestment;
import com.ragagent.financial.repository.CashDepositRepository;
import com.ragagent.financial.repository.CryptoInvestmentRepository;
import com.ragagent.financial.repository.StockInvestmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final CashDepositRepository      depositRepo;
    private final StockInvestmentRepository  stockRepo;
    private final CryptoInvestmentRepository cryptoRepo;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CashDeposit> listDeposits(String ownerEmail) {
        return depositRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
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

    // ── Stocks ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StockInvestment> listStocks(String ownerEmail) {
        return stockRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
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

    // ── Crypto ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CryptoInvestment> listCrypto(String ownerEmail) {
        return cryptoRepo.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void checkOwner(String recordEmail, String callerEmail) {
        if (recordEmail != null && callerEmail != null
                && !recordEmail.equalsIgnoreCase(callerEmail)) {
            throw new SecurityException("Only the owner can modify this record.");
        }
    }
}
