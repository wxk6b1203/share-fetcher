package com.github.ak.fetcher.controller;

import com.github.ak.fetcher.dto.StockWithFundamental;
import com.github.ak.fetcher.service.StockService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 拉取股票列表（A股基本信息）
     * POST /api/fetch/stock-list
     */
    @PostMapping("/fetch/stock-list")
    public Mono<Map<String, Object>> fetchStockList() {
        return stockService.fetchStockList()
                .map(count -> Map.of(
                        "success", true,
                        "count", count
                ));
    }

    /**
     * 拉取A股实时行情（个股快照）
     * POST /api/fetch/spot
     */
    @PostMapping("/fetch/spot")
    public Mono<Map<String, Object>> fetchSpot() {
        return stockService.fetchStockSpot()
                .map(count -> Map.of(
                        "success", true,
                        "count", count
                ));
    }

    /**
     * 拉取股票日线数据
     * POST /api/fetch/daily
     * Body: {"symbol": "sh600000", "startDate": "20230101", "endDate": "20231231", "adjust": ""}
     */
    @PostMapping("/fetch/daily")
    public Mono<Map<String, Object>> fetchDaily(@RequestBody Map<String, String> request) {
        String symbol = request.get("symbol");
        String startDate = request.getOrDefault("startDate", "20230101");
        String endDate = request.getOrDefault("endDate", "20231231");
        String adjust = request.getOrDefault("adjust", "");

        return stockService.fetchStockDaily(symbol, startDate, endDate, adjust)
                .map(count -> Map.of(
                        "success", true,
                        "symbol", symbol,
                        "count", count
                ));
    }

    /**
     * 拉取单只股票基本面数据
     * POST /api/fetch/fundamental
     * Body: {"symbol": "SH601127"}
     */
    @PostMapping("/fetch/fundamental")
    public Mono<Map<String, Object>> fetchFundamental(@RequestBody Map<String, String> request) {
        String symbol = request.get("symbol");

        return stockService.fetchStockFundamental(symbol)
                .map(count -> Map.of(
                        "success", true,
                        "symbol", symbol,
                        "count", count
                ));
    }

    /**
     * 批量拉取所有股票基本面数据
     * POST /api/fetch/fundamentals
     */
    @PostMapping("/fetch/fundamentals")
    public Mono<Map<String, Object>> fetchAllFundamentals() {
        return stockService.fetchAllStockFundamentals()
                .map(count -> Map.of(
                        "success", true,
                        "count", count
                ));
    }

    /**
     * 查询股票基本信息及关联的基本面数据
     * POST /api/stock/with-fundamental
     * Body: {"code": "000111"} 或 {"code": "sh000111"}
     */
    @PostMapping("/stock/with-fundamental")
    public Mono<Map<String, Object>> getStockWithFundamental(@RequestBody Map<String, String> request) {
        String code = request.get("code");

        return stockService.getStockWithFundamental(code)
                .<Map<String, Object>>map(stock -> {
                    if (stock.getBasic() == null && stock.getFundamental() == null) {
                        return Map.of(
                                "success", false,
                                "message", "股票不存在: " + code
                        );
                    }
                    return Map.of(
                            "success", true,
                            "data", stock
                    );
                })
                .defaultIfEmpty(Map.of(
                        "success", false,
                        "message", "股票不存在: " + code
                ));
    }
}
