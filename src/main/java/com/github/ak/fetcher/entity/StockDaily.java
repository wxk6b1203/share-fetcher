package com.github.ak.fetcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票日线行情表
 * 数据来源: stock_zh_a_hist (AKShare)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_daily")
public class StockDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;           // 股票代码 (6位纯数字)
    private LocalDate tradeDate;   // 交易日期
    private BigDecimal open;       // 开盘价
    private BigDecimal high;       // 最高价
    private BigDecimal low;        // 最低价
    private BigDecimal close;      // 收盘价
    private BigDecimal volume;     // 成交量 (手)
    private BigDecimal amount;     // 成交额 (元)
    private BigDecimal outstandingShare;  // 流通股本 (注: AKShare原数据为振幅)
    private BigDecimal turnover;    // 涨跌幅 (%)
    private String adjustFlag;     // 复权类型 (空/qfq/hfq, 前复权/后复权)
    private LocalDateTime createdAt;   // 创建时间
}
