-- ── Financial portfolio tables ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS financial_cash_deposits (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    platform        VARCHAR(255)   NOT NULL,
    platform_type   VARCHAR(100)   NOT NULL,
    country_region  VARCHAR(100),
    deposit_type    VARCHAR(10)    NOT NULL,   -- FIXED | FLEX
    currency        VARCHAR(10)    NOT NULL,
    amount          DECIMAL(19,4)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_dep_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_stocks (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    broker          VARCHAR(255)   NOT NULL,
    stock_type      VARCHAR(20)    NOT NULL,   -- US_STOCK | HK_STOCK | CN_STOCK | JP_STOCK | FR_STOCK
    symbol          VARCHAR(20)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    stock_amount    DECIMAL(19,4)  NOT NULL,
    invest_amount   DECIMAL(19,4)  NOT NULL,
    currency        VARCHAR(10)    NOT NULL,
    fee             DECIMAL(19,4)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_stk_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_crypto (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    symbol          VARCHAR(30)    NOT NULL,
    amount          DECIMAL(28,8)  NOT NULL,
    invest_amount   DECIMAL(19,4)  NOT NULL,
    currency        VARCHAR(10)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_cry_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_futures (
    id                  VARCHAR(36)    PRIMARY KEY,
    owner_uuid          VARCHAR(36)    NOT NULL,
    exchange_kind       VARCHAR(20)    NOT NULL,   -- SECURITY | CRYPTO_CEX | CRYPTO_DEX
    exchange            VARCHAR(20)    NOT NULL,   -- IBKR | BINANCE | OKX | KRAKEN | HYPERLIQUID | JUPITER_PERPS | LIGHTER
    symbol              VARCHAR(30),               -- null for CRYPTO_DEX (address-only row)
    side                VARCHAR(5),                -- LONG | SHORT; null for CRYPTO_DEX
    quantity            DECIMAL(28,8),              -- contracts/size; null for CRYPTO_DEX
    entry_price         DECIMAL(19,4),              -- null for CRYPTO_DEX
    leverage            DECIMAL(6,2),               -- optional, manual kinds only
    currency            VARCHAR(10)    NOT NULL DEFAULT 'USD',
    connection_address  VARCHAR(255),               -- wallet address; CRYPTO_DEX only
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_fut_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_cards (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    bank            VARCHAR(255)   NOT NULL,
    country_region  VARCHAR(100),
    types           VARCHAR(50)    NOT NULL,   -- comma-separated: Credit,Debit,ATM
    card_name       VARCHAR(255)   NOT NULL,
    network         VARCHAR(20)    NOT NULL,   -- Mastercard | Visa | UnionPay | JCB | AMEX
    expire_date     VARCHAR(7),                -- YYYY-MM format, nullable
    credit_limit             DECIMAL(19,2),  -- nullable
    credit_limit_currency    VARCHAR(10),    -- nullable; currency of the credit limit
    shared_credit            TINYINT(1),    -- NULL=unknown, 1=shared pool, 0=dedicated
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_card_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS salary_usage_records (
    id                           VARCHAR(36)   PRIMARY KEY,
    owner_uuid                   VARCHAR(36)   NOT NULL,
    year                         INT           NOT NULL,
    month                        INT           NOT NULL,
    region                       VARCHAR(100)  NOT NULL,
    currency                     VARCHAR(10)   NOT NULL,
    salary                       DECIMAL(19,2) NOT NULL DEFAULT 0,
    bonus                        DECIMAL(19,2) NOT NULL DEFAULT 0,
    retirement_saving_employee   DECIMAL(19,2) NOT NULL DEFAULT 0,
    retirement_saving_employer   DECIMAL(19,2) NOT NULL DEFAULT 0,
    tax                          DECIMAL(19,2) NOT NULL DEFAULT 0,
    house_rent                   DECIMAL(19,2) NOT NULL DEFAULT 0,
    living_expense               DECIMAL(19,2) NOT NULL DEFAULT 0,
    other_expense                DECIMAL(19,2) NOT NULL DEFAULT 0,
    total_expense                DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sal_owner (owner_uuid),
    UNIQUE KEY uq_sal_owner_ym (owner_uuid, year, month)
);
