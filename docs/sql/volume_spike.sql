-- 查询最近10天内买量放大40%以上的股票（6或0开头）
-- 买量放大定义：当天成交量 > 前一天成交量 * 1.4

WITH recent_volumes AS (
    SELECT
        code,
        trade_date,
        volume,
        LAG(volume, 1) OVER (
            PARTITION BY code
            ORDER BY trade_date
        ) as prev_volume
    FROM stock_daily
    WHERE trade_date >= date('now', '-10 days')
      AND (code LIKE '6%' OR code LIKE '0%')
),
volume_spike AS (
    SELECT
        code,
        trade_date,
        volume,
        prev_volume,
        ROUND((volume / prev_volume - 1) * 100, 2) as pct_increase
    FROM recent_volumes
    WHERE prev_volume IS NOT NULL
      AND prev_volume > 0
      AND volume > prev_volume * 1.4
)
SELECT
    s.code,
    b.name,
    s.trade_date,
    s.prev_volume as yesterday_volume,
    s.volume as today_volume,
    s.pct_increase
FROM volume_spike s
JOIN stock_basic b ON s.code = b.code
ORDER BY s.pct_increase DESC
LIMIT 100;
