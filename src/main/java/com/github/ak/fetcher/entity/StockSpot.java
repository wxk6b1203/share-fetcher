package com.github.ak.fetcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票实时行情快照表 (当日行情)
 * 数据来源: stock_zh_a_spot_em (东方财富)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_spot")
public class StockSpot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;               // 股票代码
    private String name;               // 股票名称
    private BigDecimal latestPrice;    // 最新价 (元)
    private BigDecimal changePct;      // 涨跌幅 (%)
    private BigDecimal changeAmount;   // 涨跌额 (元)
    private BigDecimal volume;         // 成交量 (手)
    private BigDecimal amount;         // 成交额 (元)
    private BigDecimal amplitude;      // 振幅 (%)
    private BigDecimal high;           // 最高价 (元)
    private BigDecimal low;            // 最低价 (元)
    private BigDecimal openPrice;     // 今开价 (元)
    private BigDecimal prevClose;     // 昨收价 (元)
    private BigDecimal volumeRatio;   // 量比
    private BigDecimal turnoverRate;  // 换手率 (%)
    private BigDecimal peDynamic;     // 市盈率-动态
    private BigDecimal pb;            // 市净率
    private BigDecimal totalMarketValue;   // 总市值 (元)
    private BigDecimal circMarketValue;    // 流通市值 (元)
    private BigDecimal speedPct;      // 涨速 (%)
    private BigDecimal change5minPct; // 5分钟涨跌 (%)
    private BigDecimal change60dPct; // 60日涨跌幅 (%)
    private BigDecimal changeYtdPct; // 年初至今涨跌幅 (%)
    private LocalDateTime tradeTime;  // 行情时间
    private LocalDateTime createdAt;  // 创建时间
}
