package com.agentsystem.financial.controller;

import com.agentsystem.financial.config.FinanceServiceProperties;
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
import com.agentsystem.financial.service.FinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal-only API consumed by agent-system-rest's FinanceInnerClient.
 * Auth: shared {@code X-Finance-Key} header — NOT a user JWT. The caller (agent-system-rest)
 * has already resolved ownerUuid (from the JWT) and defaultCurrency (from user preferences)
 * before calling — this service never looks either up itself, so it stays free of any
 * dependency on the user/org packages.
 */
@RestController
@RequestMapping("/internal/financial")
@RequiredArgsConstructor
public class InternalFinancialController {

    private final FinancialService        service;
    private final FinanceServiceProperties serviceProperties;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @GetMapping("/deposits")
    public ResponseEntity<List<CashDepositDto>> listDeposits(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestParam String currency) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listDeposits(ownerUuid, currency));
    }

    @PostMapping("/deposits")
    public ResponseEntity<CashDeposit> createDeposit(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createDeposit(ownerUuid, body));
    }

    @PutMapping("/deposits/{id}")
    public ResponseEntity<CashDeposit> updateDeposit(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateDeposit(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteDeposit(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    public ResponseEntity<List<StockInvestmentDto>> listStocks(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestParam String currency) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listStocks(ownerUuid, currency));
    }

    @PostMapping("/stocks")
    public ResponseEntity<StockInvestment> createStock(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createStock(ownerUuid, body));
    }

    @PutMapping("/stocks/{id}")
    public ResponseEntity<StockInvestment> updateStock(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateStock(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/stocks/{id}")
    public ResponseEntity<Void> deleteStock(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteStock(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/stocks/lookup")
    public ResponseEntity<Map<String, String>> lookupStock(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String symbol) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return service.lookupStockName(symbol)
                .map(name -> ResponseEntity.ok(Map.of("name", name)))
                .orElseGet(() -> ResponseEntity.ok(Map.of()));
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @GetMapping("/crypto")
    public ResponseEntity<List<CryptoInvestmentDto>> listCrypto(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestParam String currency) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listCrypto(ownerUuid, currency));
    }

    @PostMapping("/crypto")
    public ResponseEntity<CryptoInvestment> createCrypto(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createCrypto(ownerUuid, body));
    }

    @PutMapping("/crypto/{id}")
    public ResponseEntity<CryptoInvestment> updateCrypto(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateCrypto(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/crypto/{id}")
    public ResponseEntity<Void> deleteCrypto(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteCrypto(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    @GetMapping("/futures")
    public ResponseEntity<List<FutureInvestmentDto>> listFutures(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestParam String currency) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listFutures(ownerUuid, currency));
    }

    @PostMapping("/futures")
    public ResponseEntity<FutureInvestment> createFuture(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createFuture(ownerUuid, body));
    }

    @PutMapping("/futures/{id}")
    public ResponseEntity<FutureInvestment> updateFuture(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateFuture(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/futures/{id}")
    public ResponseEntity<Void> deleteFuture(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteFuture(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @GetMapping("/cards")
    public ResponseEntity<List<CardDto>> listCards(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listCards(ownerUuid));
    }

    @PostMapping("/cards")
    public ResponseEntity<Card> createCard(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createCard(ownerUuid, body));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<Card> updateCard(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateCard(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteCard(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @GetMapping("/salary")
    public ResponseEntity<List<SalaryUsageRecordDto>> listSalary(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listSalary(ownerUuid));
    }

    @PostMapping("/salary")
    public ResponseEntity<SalaryUsageRecord> createSalary(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid, @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        return ResponseEntity.status(201).body(service.createSalary(ownerUuid, body));
    }

    @PutMapping("/salary/{id}")
    public ResponseEntity<SalaryUsageRecord> updateSalary(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid,
            @RequestBody Map<String, Object> body) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.updateSalary(id, ownerUuid, body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/salary/{id}")
    public ResponseEntity<Void> deleteSalary(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @PathVariable String id, @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        try {
            service.deleteSalary(id, ownerUuid);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @PostMapping("/prices/refresh")
    public ResponseEntity<Map<String, String>> refreshPrices(
            @RequestHeader(value = "X-Finance-Key", required = false) String key,
            @RequestParam String ownerUuid) {
        if (!validKey(key)) return ResponseEntity.status(401).build();
        service.refreshPrices(ownerUuid);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean validKey(String key) {
        return key != null && key.equals(serviceProperties.serviceKey());
    }
}
