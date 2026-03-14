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
 * 数据来源: stock_zh_a_spot (东方财富)
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
    private BigDecimal bid;           // 买入价 (元)
    private BigDecimal ask;           // 卖出价 (元)
    private BigDecimal volume;         // 成交量 (手)
    private BigDecimal amount;         // 成交额 (元)
    private BigDecimal high;           // 最高价 (元)
    private BigDecimal low;            // 最低价 (元)
    private BigDecimal openPrice;     // 今开价 (元)
    private BigDecimal prevClose;     // 昨收价 (元)
    private String timestamp;         // 时间戳 (HH:mm:ss)
    private LocalDateTime createdAt;  // 创建时间
}
