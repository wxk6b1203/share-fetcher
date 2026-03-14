-- A股股票基本信息表
CREATE TABLE IF NOT EXISTS stock_basic (
    code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    market VARCHAR(20),
    list_date VARCHAR(10),
    delist_date VARCHAR(10),
    is_hs VARCHAR(1),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- A股日线行情表
CREATE TABLE IF NOT EXISTS stock_daily (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(10) NOT NULL,
    trade_date DATE NOT NULL,
    open DECIMAL(10, 2),
    high DECIMAL(10, 2),
    low DECIMAL(10, 2),
    close DECIMAL(10, 2),
    volume DECIMAL(20, 2),
    amount DECIMAL(20, 2),
    outstanding_share DECIMAL(20, 2),
    turnover DECIMAL(10, 6),
    adjust_flag VARCHAR(4) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(code, trade_date, adjust_flag)
);

CREATE INDEX IF NOT EXISTS idx_stock_daily_code ON stock_daily(code);
CREATE INDEX IF NOT EXISTS idx_stock_daily_date ON stock_daily(trade_date);

-- A股实时行情表（个股快照）
CREATE TABLE IF NOT EXISTS stock_spot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100),
    latest_price DECIMAL(20, 2),
    change_pct DECIMAL(20, 2),
    change_amount DECIMAL(20, 2),
    bid DECIMAL(20, 2),
    ask DECIMAL(20, 2),
    volume DECIMAL(20, 2),
    amount DECIMAL(20, 2),
    high DECIMAL(20, 2),
    low DECIMAL(20, 2),
    open_price DECIMAL(20, 2),
    prev_close DECIMAL(20, 2),
    timestamp VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_spot_code ON stock_spot(code);

-- 股票基本面信息表（雪球）
CREATE TABLE IF NOT EXISTS stock_fundamental (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(10) NOT NULL,
    org_id VARCHAR(50),
    org_name_cn VARCHAR(200),
    org_short_name_cn VARCHAR(100),
    org_name_en VARCHAR(200),
    org_short_name_en VARCHAR(100),
    main_operation_business TEXT,
    operating_scope TEXT,
    district_encode VARCHAR(20),
    org_cn_introduction TEXT,
    legal_representative VARCHAR(100),
    general_manager VARCHAR(100),
    secretary VARCHAR(100),
    established_date VARCHAR(20),
    reg_asset DECIMAL(20, 2),
    staff_num INTEGER,
    telephone VARCHAR(50),
    postcode VARCHAR(20),
    fax VARCHAR(50),
    email VARCHAR(100),
    org_website VARCHAR(200),
    reg_address_cn VARCHAR(500),
    office_address_cn VARCHAR(500),
    currency VARCHAR(10),
    listed_date VARCHAR(20),
    provincial_name VARCHAR(50),
    actual_controller VARCHAR(200),
    classi_name VARCHAR(50),
    pre_name_cn VARCHAR(200),
    chairman VARCHAR(100),
    executives_nums INTEGER,
    actual_issue_vol DECIMAL(20, 2),
    issue_price DECIMAL(10, 2),
    actual_rc_net_amt DECIMAL(20, 2),
    affiliate_industry VARCHAR(200),
    version INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_fundamental_code ON stock_fundamental(code);

-- 任务表
CREATE TABLE IF NOT EXISTS task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_type VARCHAR(50) NOT NULL,
    input_params TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_task_status ON task(status);
CREATE INDEX IF NOT EXISTS idx_task_type ON task(task_type);
