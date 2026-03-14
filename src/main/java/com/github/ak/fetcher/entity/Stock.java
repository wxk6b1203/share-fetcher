package com.github.ak.fetcher.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 股票简单实体 (仅包含代码和名称)
 * 注: 推荐使用 StockBasic 替代
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Stock {
    @TableId
    private String code;   // 股票代码
    private String name;   // 股票名称
}
