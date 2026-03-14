package com.github.ak.fetcher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票基本信息表
 * 数据来源: stock_info_a_code_name (AKShare)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_basic")
public class StockBasic {
    @TableId(type = IdType.INPUT)
    private String code;          // 股票代码 (6位纯数字, 如: 600031, 000776)
    private String name;           // 股票名称 (如: 三一重工, 广发证券)
    private String market;         // 交易所市场 (上交所/深交所/北交所)
    private String listDate;      // 上市日期 (格式: YYYYMMDD)
    private String delistDate;    // 退市日期 (格式: YYYYMMDD, 空表示在上市)
    private String isHs;          // 是否沪深港通标的 (N/Y/空)
    private LocalDateTime createdAt;  // 创建时间
    private LocalDateTime updatedAt;  // 更新时间
}
