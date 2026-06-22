package com.agentsystem.financial.service;

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

import java.util.List;
import java.util.Map;

public interface FinancialService {

    List<CashDepositDto> listDeposits(String ownerEmail, String defaultCurrency);

    CashDeposit createDeposit(String ownerEmail, Map<String, Object> body);

    CashDeposit updateDeposit(String id, String ownerEmail, Map<String, Object> body);

    void deleteDeposit(String id, String ownerEmail);

    List<StockInvestmentDto> listStocks(String ownerEmail, String defaultCurrency);

    StockInvestment createStock(String ownerEmail, Map<String, Object> body);

    StockInvestment updateStock(String id, String ownerEmail, Map<String, Object> body);

    void deleteStock(String id, String ownerEmail);

    List<CryptoInvestmentDto> listCrypto(String ownerEmail, String defaultCurrency);

    CryptoInvestment createCrypto(String ownerEmail, Map<String, Object> body);

    CryptoInvestment updateCrypto(String id, String ownerEmail, Map<String, Object> body);

    void deleteCrypto(String id, String ownerEmail);

    List<CardDto> listCards(String ownerEmail);

    Card createCard(String ownerEmail, Map<String, Object> body);

    Card updateCard(String id, String ownerEmail, Map<String, Object> body);

    void deleteCard(String id, String ownerEmail);

    List<SalaryUsageRecordDto> listSalary(String ownerEmail);

    SalaryUsageRecord createSalary(String ownerEmail, Map<String, Object> body);

    SalaryUsageRecord updateSalary(String id, String ownerEmail, Map<String, Object> body);

    void deleteSalary(String id, String ownerEmail);

    void refreshPrices(String ownerEmail);
}
