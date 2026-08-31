package com.agentsystem.financial.service;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FinancialService {

    List<CashDepositDto> listDeposits(String ownerUuid, String defaultCurrency);

    CashDeposit createDeposit(String ownerUuid, Map<String, Object> body);

    CashDeposit updateDeposit(String id, String ownerUuid, Map<String, Object> body);

    void deleteDeposit(String id, String ownerUuid);

    List<StockInvestmentDto> listStocks(String ownerUuid, String defaultCurrency);

    StockInvestment createStock(String ownerUuid, Map<String, Object> body);

    StockInvestment updateStock(String id, String ownerUuid, Map<String, Object> body);

    void deleteStock(String id, String ownerUuid);

    /** Looks up a stock ticker's company display name, for the Add Stock form's auto-fill. */
    Optional<String> lookupStockName(String symbol);

    List<CryptoInvestmentDto> listCrypto(String ownerUuid, String defaultCurrency);

    CryptoInvestment createCrypto(String ownerUuid, Map<String, Object> body);

    CryptoInvestment updateCrypto(String id, String ownerUuid, Map<String, Object> body);

    void deleteCrypto(String id, String ownerUuid);

    List<FutureInvestmentDto> listFutures(String ownerUuid, String defaultCurrency);

    FutureInvestment createFuture(String ownerUuid, Map<String, Object> body);

    FutureInvestment updateFuture(String id, String ownerUuid, Map<String, Object> body);

    void deleteFuture(String id, String ownerUuid);

    List<CardDto> listCards(String ownerUuid);

    Card createCard(String ownerUuid, Map<String, Object> body);

    Card updateCard(String id, String ownerUuid, Map<String, Object> body);

    void deleteCard(String id, String ownerUuid);

    List<SalaryUsageRecordDto> listSalary(String ownerUuid);

    SalaryUsageRecord createSalary(String ownerUuid, Map<String, Object> body);

    SalaryUsageRecord updateSalary(String id, String ownerUuid, Map<String, Object> body);

    void deleteSalary(String id, String ownerUuid);

    void refreshPrices(String ownerUuid);
}
