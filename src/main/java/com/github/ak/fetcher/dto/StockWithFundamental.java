package com.github.ak.fetcher.dto;

import com.github.ak.fetcher.entity.StockBasic;
import com.github.ak.fetcher.entity.StockFundamental;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockWithFundamental {
    private StockBasic basic;
    private StockFundamental fundamental;
}
