package com.ragagent.financial;

import com.ragagent.financial.entity.CashDeposit;
import com.ragagent.financial.entity.CryptoInvestment;
import com.ragagent.financial.entity.StockInvestment;
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

    private final FinancialService service;

    // ── Cash Deposits ─────────────────────────────────────────────────────────

    @GetMapping("/deposits")
    @Operation(summary = "List cash deposits for the authenticated user")
    public ResponseEntity<List<CashDeposit>> listDeposits(HttpServletRequest req) {
        return ResponseEntity.ok(service.listDeposits(email(req)));
    }

    @PostMapping("/deposits")
    @Operation(summary = "Create a cash deposit")
    public ResponseEntity<CashDeposit> createDeposit(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createDeposit(email(req), body));
    }

    @PutMapping("/deposits/{id}")
    @Operation(summary = "Update a cash deposit")
    public ResponseEntity<CashDeposit> updateDeposit(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateDeposit(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/deposits/{id}")
    @Operation(summary = "Delete a cash deposit")
    public ResponseEntity<Void> deleteDeposit(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteDeposit(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Stocks ────────────────────────────────────────────────────────────────

    @GetMapping("/stocks")
    @Operation(summary = "List stock investments for the authenticated user")
    public ResponseEntity<List<StockInvestment>> listStocks(HttpServletRequest req) {
        return ResponseEntity.ok(service.listStocks(email(req)));
    }

    @PostMapping("/stocks")
    @Operation(summary = "Create a stock investment")
    public ResponseEntity<StockInvestment> createStock(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createStock(email(req), body));
    }

    @PutMapping("/stocks/{id}")
    @Operation(summary = "Update a stock investment")
    public ResponseEntity<StockInvestment> updateStock(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateStock(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/stocks/{id}")
    @Operation(summary = "Delete a stock investment")
    public ResponseEntity<Void> deleteStock(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteStock(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    @GetMapping("/crypto")
    @Operation(summary = "List crypto investments for the authenticated user")
    public ResponseEntity<List<CryptoInvestment>> listCrypto(HttpServletRequest req) {
        return ResponseEntity.ok(service.listCrypto(email(req)));
    }

    @PostMapping("/crypto")
    @Operation(summary = "Create a crypto investment")
    public ResponseEntity<CryptoInvestment> createCrypto(
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        return ResponseEntity.status(201).body(service.createCrypto(email(req), body));
    }

    @PutMapping("/crypto/{id}")
    @Operation(summary = "Update a crypto investment")
    public ResponseEntity<CryptoInvestment> updateCrypto(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest req) {
        try {
            return ResponseEntity.ok(service.updateCrypto(id, email(req), body));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/crypto/{id}")
    @Operation(summary = "Delete a crypto investment")
    public ResponseEntity<Void> deleteCrypto(@PathVariable String id, HttpServletRequest req) {
        try {
            service.deleteCrypto(id, email(req));
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String email(HttpServletRequest req) {
        String email = (String) req.getAttribute("authenticatedEmail");
        return email != null ? email : "anonymous";
    }
}
