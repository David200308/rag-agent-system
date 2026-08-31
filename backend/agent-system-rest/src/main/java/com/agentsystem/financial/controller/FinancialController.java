package com.agentsystem.financial.controller;

import com.agentsystem.financial.service.FinancialService;

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
import com.agentsystem.org.OrgContext;
import com.agentsystem.user.service.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/financial")
@RequiredArgsConstructor
@Tag(name = "Financial", description = "Financial portfolio management")
public class FinancialController {

    private final FinancialService      service;
    private final UserPreferenceService prefService;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @GetMapping("/deposits")
    @Operation(summary = "List cash deposits with amounts converted to the user's default currency")
    public ResponseEntity<List<CashDepositDto>> listDeposits(HttpServletRequest req) {
        return ResponseEntity.ok(service.listDeposits(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Create a cash deposit")
    public ResponseEntity<CashDeposit> createDeposit(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createDeposit(ownerUuid(req), body));
    }

    @PutMapping("/deposits/{id}")
    public ResponseEntity<CashDeposit> updateDeposit(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateDeposit(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/deposits/{id}")
    public ResponseEntity<Void> deleteDeposit(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteDeposit(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    @Operation(summary = "List stocks with live prices and converted amounts")
    public ResponseEntity<List<StockInvestmentDto>> listStocks(HttpServletRequest req) {
        return ResponseEntity.ok(service.listStocks(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/stocks")
    public ResponseEntity<StockInvestment> createStock(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createStock(ownerUuid(req), body));
    }

    @PutMapping("/stocks/{id}")
    public ResponseEntity<StockInvestment> updateStock(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateStock(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/stocks/{id}")
    public ResponseEntity<Void> deleteStock(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteStock(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/stocks/lookup")
    @Operation(summary = "Look up a stock ticker's company display name, for Add Stock form auto-fill")
    public ResponseEntity<Map<String, String>> lookupStock(@RequestParam String symbol) {
        return service.lookupStockName(symbol)
                .map(name -> ResponseEntity.ok(Map.of("name", name)))
                .orElseGet(() -> ResponseEntity.ok(Map.of()));
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @GetMapping("/crypto")
    @Operation(summary = "List crypto investments with live prices and converted amounts")
    public ResponseEntity<List<CryptoInvestmentDto>> listCrypto(HttpServletRequest req) {
        return ResponseEntity.ok(service.listCrypto(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/crypto")
    public ResponseEntity<CryptoInvestment> createCrypto(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createCrypto(ownerUuid(req), body));
    }

    @PutMapping("/crypto/{id}")
    public ResponseEntity<CryptoInvestment> updateCrypto(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateCrypto(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/crypto/{id}")
    public ResponseEntity<Void> deleteCrypto(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteCrypto(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Futures ───────────────────────────────────────────────────────────────

    @GetMapping("/futures")
    @Operation(summary = "List futures positions (Security/CEX manual entries + live-tracked Hyperliquid DEX positions)")
    public ResponseEntity<List<FutureInvestmentDto>> listFutures(HttpServletRequest req) {
        return ResponseEntity.ok(service.listFutures(ownerUuid(req), defaultCurrency(req)));
    }

    @PostMapping("/futures")
    public ResponseEntity<FutureInvestment> createFuture(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createFuture(ownerUuid(req), body));
    }

    @PutMapping("/futures/{id}")
    public ResponseEntity<FutureInvestment> updateFuture(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateFuture(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/futures/{id}")
    public ResponseEntity<Void> deleteFuture(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteFuture(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @GetMapping("/cards")
    @Operation(summary = "List cards")
    public ResponseEntity<List<CardDto>> listCards(HttpServletRequest req) {
        return ResponseEntity.ok(service.listCards(ownerUuid(req)));
    }

    @PostMapping("/cards")
    @Operation(summary = "Create a card")
    public ResponseEntity<Card> createCard(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createCard(ownerUuid(req), body));
    }

    @PutMapping("/cards/{id}")
    public ResponseEntity<Card> updateCard(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateCard(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/cards/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteCard(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Salary Usage Records ──────────────────────────────────────────────────

    @GetMapping("/salary")
    @Operation(summary = "List salary usage records")
    public ResponseEntity<List<SalaryUsageRecordDto>> listSalary(HttpServletRequest req) {
        return ResponseEntity.ok(service.listSalary(ownerUuid(req)));
    }

    @PostMapping("/salary")
    @Operation(summary = "Create a salary usage record")
    public ResponseEntity<SalaryUsageRecord> createSalary(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createSalary(ownerUuid(req), body));
    }

    @PutMapping("/salary/{id}")
    public ResponseEntity<SalaryUsageRecord> updateSalary(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateSalary(id, ownerUuid(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/salary/{id}")
    public ResponseEntity<Void> deleteSalary(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteSalary(id, ownerUuid(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Price refresh ─────────────────────────────────────────────────────────

    @PostMapping("/prices/refresh")
    @Operation(summary = "Force-refresh live market prices for all of the user's symbols")
    public ResponseEntity<Map<String, String>> refreshPrices(HttpServletRequest req) {
        service.refreshPrices(ownerUuid(req));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String ownerUuid(HttpServletRequest req) {
        String uuid = (String) req.getAttribute("authenticatedUserUuid");
        return uuid != null ? uuid : "anonymous";
    }

    private ResponseEntity<Map<String, String>> requirePersonalMode(HttpServletRequest req) {
        OrgContext ctx = OrgContext.from(req);
        if (ctx.isTeam()) {
            return ResponseEntity.status(403).body(Map.of("error", "Financial is not available in team mode."));
        }
        return null;
    }

    private String defaultCurrency(HttpServletRequest req) {
        String cur = prefService.getOrDefault(ownerUuid(req)).getDefaultCurrency();
        return (cur != null && !cur.isBlank()) ? cur : "USD";
    }
}
