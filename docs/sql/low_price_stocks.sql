-- 查询最近80天处于历史低位的股票
-- 历史低位定义：当前收盘价接近80天内最低价（在最低价115%以内）

WITH latest_data AS (
    -- 获取每只股票最新的交易日期和收盘价
    SELECT code, trade_date, close
    FROM stock_daily
    WHERE (code, trade_date) IN (
        SELECT code, MAX(trade_date)
        FROM stock_daily
        WHERE trade_date >= date('now', '-80 days')
        GROUP BY code
    )
),
min_price_80d AS (
    -- 获取每只股票最近80天的最低收盘价
    SELECT code, MIN(close) as min_close_80d
    FROM stock_daily
    WHERE trade_date >= date('now', '-80 days')
    GROUP BY code
),
low_price_stocks AS (
    -- 筛选当前价格接近80天最低价的股票（低于115%）
    SELECT
        l.code,
        b.name,
        l.trade_date as latest_date,
        l.close as latest_close,
        m.min_close_80d,
        ROUND((l.close / m.min_close_80d - 1) * 100, 2) as pct_above_min,
        ROUND(m.min_close_80d * 1.15, 2) as threshold_115
    FROM latest_data l
    JOIN min_price_80d m ON l.code = m.code
    JOIN stock_basic b ON l.code = b.code
    WHERE l.close <= m.min_close_80d * 1.15
      AND l.close IS NOT NULL
      AND m.min_close_80d IS NOT NULL
      AND m.min_close_80d > 0
      AND b.name NOT LIKE '%*ST%'
      AND b.name NOT LIKE '%ST%'
)
SELECT
    code,
    name,
    latest_date,
    latest_close,
    min_close_80d,
    pct_above_min,
    threshold_115,
    CASE
        WHEN pct_above_min <= 0 THEN '★★★★★ 跌破80天最低'
        WHEN pct_above_min <= 3 THEN '★★★★ 接近最低'
        WHEN pct_above_min <= 5 THEN '★★★ 低于5%'
        WHEN pct_above_min <= 10 THEN '★★ 低于10%'
        ELSE '★ 低于15%'
    END as low_level
FROM low_price_stocks
ORDER BY pct_above_min ASC, latest_close ASC
LIMIT 100;
