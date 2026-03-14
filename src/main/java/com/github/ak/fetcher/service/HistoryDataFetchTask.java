package com.github.ak.fetcher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ak.fetcher.config.AkToolsConfig;
import com.github.ak.fetcher.entity.StockBasic;
import com.github.ak.fetcher.entity.StockDaily;
import com.github.ak.fetcher.mapper.StockBasicMapper;
import com.github.ak.fetcher.mapper.StockDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class HistoryDataFetchTask {
    private static final Logger log = LoggerFactory.getLogger(HistoryDataFetchTask.class);

    private final StockService stockService;
    private final StockBasicMapper stockBasicMapper;
    private final StockDailyMapper stockDailyMapper;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public HistoryDataFetchTask(StockService stockService,
                                 StockBasicMapper stockBasicMapper,
                                 StockDailyMapper stockDailyMapper,
                                 AkToolsConfig akToolsConfig,
                                 ObjectMapper objectMapper) {
        this.stockService = stockService;
        this.stockBasicMapper = stockBasicMapper;
        this.stockDailyMapper = stockDailyMapper;
        this.objectMapper = objectMapper;

        // 创建 WebClient
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(akToolsConfig.getBaseUrl() + "/api/public")
                .exchangeStrategies(strategies)
                .build();
    }

    public String execute(String inputParams) {
        try {
            JsonNode params = objectMapper.readTree(inputParams);

            String startDate = params.has("startDate") ? params.get("startDate").asText() : LocalDate.now().toString();
            String endDate = params.has("endDate") ? params.get("endDate").asText() : LocalDate.now().toString();
            String symbol = params.has("symbol") && !params.get("symbol").isNull() ? params.get("symbol").asText() : null;
            String adjust = params.has("adjust") && !params.get("adjust").isNull() ? params.get("adjust").asText() : "qfq";
            String codeStart = params.has("codeStart") && !params.get("codeStart").isNull() ? params.get("codeStart").asText() : null;
            String codeEnd = params.has("codeEnd") && !params.get("codeEnd").isNull() ? params.get("codeEnd").asText() : null;

            log.info("Executing history data fetch task: startDate={}, endDate={}, symbol={}, codeStart={}, codeEnd={}, adjust={}",
                    startDate, endDate, symbol, codeStart, codeEnd, adjust);

            List<String> symbols = new ArrayList<>();
            if (symbol != null && !symbol.isEmpty()) {
                // 优先使用指定的单个股票代码
                symbols.add(symbol);
            } else if (codeStart != null && !codeStart.isEmpty() && codeEnd != null && !codeEnd.isEmpty()) {
                // 使用股票代码范围
                QueryWrapper<StockBasic> queryWrapper = new QueryWrapper<>();
                queryWrapper.ge("code", codeStart)
                        .le("code", codeEnd)
                        .orderByAsc("code");
                List<StockBasic> rangeStocks = stockBasicMapper.selectList(queryWrapper);
                for (StockBasic stock : rangeStocks) {
                    symbols.add(stock.getCode());
                }
            } else {
                // 获取全部股票列表
                List<StockBasic> allStocks = stockBasicMapper.selectList(null);
                for (StockBasic stock : allStocks) {
                    symbols.add(stock.getCode());
                }
            }

            log.info("Will fetch data for {} symbols", symbols.size());

            int totalInserted = 0;
            for (String code : symbols) {
                try {
                    // 添加延迟避免请求过快（1-3秒随机）
                    Thread.sleep(1000 + (long) (Math.random() * 2000));

                    // 获取该股票在数据库中的最早和最晚日期
                    LocalDate dbMinDate = getDbMinDate(code);
                    LocalDate dbMaxDate = getDbMaxDate(code);

                    // 计算需要拉取的日期范围
                    LocalDate requestStart = LocalDate.parse(startDate);
                    LocalDate requestEnd = LocalDate.parse(endDate);

                    String fetchStart;
                    String fetchEnd;

                    if (dbMinDate == null && dbMaxDate == null) {
                        // 数据库没有数据，使用完整时间范围
                        fetchStart = startDate;
                        fetchEnd = endDate;
                    } else if (dbMinDate != null && requestStart.isBefore(dbMinDate)) {
                        // 传入开始时间比数据库最早时间早，需要拉取前面的数据
                        fetchStart = startDate;
                        fetchEnd = dbMinDate.minusDays(1).toString();
                    } else if (dbMaxDate != null && requestEnd.isAfter(dbMaxDate)) {
                        // 传入结束时间比数据库最晚时间晚，需要拉取后面的数据
                        fetchStart = dbMaxDate.plusDays(1).toString();
                        fetchEnd = endDate;
                    } else {
                        // 数据已完整，跳过
                        log.info("Daily data for {} is already up to date, skipping", code);
                        continue;
                    }

                    // 如果计算出的开始日期大于结束日期，说明没有需要拉取的数据
                    if (LocalDate.parse(fetchStart).isAfter(LocalDate.parse(fetchEnd))) {
                        log.info("No new data to fetch for {}", code);
                        continue;
                    }

                    log.info("Fetching incremental data for {}: {} to {}", code, fetchStart, fetchEnd);
                    int inserted = fetchAndSaveDailyData(code, fetchStart, fetchEnd, adjust);
                    totalInserted += inserted;
                    log.info("Fetched daily data for {}: {} records inserted", code, inserted);
                } catch (Exception e) {
                    log.error("Failed to fetch daily data for {}: {}", code, e.getMessage());
                }
            }

            return String.format("{\"totalInserted\": %d, \"symbolsProcessed\": %d}", totalInserted, symbols.size());
        } catch (Exception e) {
            log.error("History data fetch task failed", e);
            throw new RuntimeException("Task execution failed: " + e.getMessage(), e);
        }
    }

    private int fetchAndSaveDailyData(String code, String startDate, String endDate, String adjustFlag) {
        // 6开头是上海，0、3开头是深圳
        String symbol = code.startsWith("6") ? "sh" + code : "sz" + code;

        Mono<String> responseMono = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock_zh_a_daily")
                        .queryParam("symbol", symbol)
                        .queryParam("start_date", startDate.replace("-", ""))
                        .queryParam("end_date", endDate.replace("-", ""))
                        .queryParam("adjust", adjustFlag)
                        .build())
                .retrieve()
                .bodyToMono(String.class);

        String response = responseMono.block();
        return parseAndSaveDailyData(response, code, adjustFlag);
    }

    private int parseAndSaveDailyData(String jsonResponse, String code, String adjustFlag) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.isArray() ? root : root.get("data");

            if (data == null || !data.isArray()) {
                return 0;
            }

            int insertedCount = 0;
            for (JsonNode row : data) {
                String dateStr = row.get("date").asText();
                if (dateStr.contains("T")) {
                    dateStr = dateStr.split("T")[0];
                }
                LocalDate tradeDate = LocalDate.parse(dateStr);

                // 跳过已存在的记录
                QueryWrapper<StockDaily> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("code", code)
                        .eq("trade_date", tradeDate);
                StockDaily existing = stockDailyMapper.selectOne(queryWrapper);
                if (existing != null) {
                    continue;
                }

                StockDaily daily = new StockDaily();
                daily.setCode(code);
                daily.setTradeDate(tradeDate);
                daily.setOpen(getBigDecimal(row, "open"));
                daily.setClose(getBigDecimal(row, "close"));
                daily.setHigh(getBigDecimal(row, "high"));
                daily.setLow(getBigDecimal(row, "low"));
                daily.setVolume(getBigDecimal(row, "volume"));
                daily.setAmount(getBigDecimal(row, "amount"));
                daily.setOutstandingShare(getBigDecimal(row, "outstanding_share"));
                daily.setTurnover(getBigDecimal(row, "turnover"));
                daily.setAdjustFlag(adjustFlag);

                stockDailyMapper.insert(daily);
                insertedCount++;
            }

            return insertedCount;
        } catch (Exception e) {
            throw new RuntimeException("解析股票数据失败: " + e.getMessage(), e);
        }
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.asText());
    }

    /**
     * 获取数据库中该股票的最早日期
     */
    private LocalDate getDbMinDate(String code) {
        QueryWrapper<StockDaily> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", code)
                .orderByAsc("trade_date")
                .last("LIMIT 1");
        StockDaily first = stockDailyMapper.selectOne(queryWrapper);
        return first != null ? first.getTradeDate() : null;
    }

    /**
     * 获取数据库中该股票的最晚日期
     */
    private LocalDate getDbMaxDate(String code) {
        QueryWrapper<StockDaily> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", code)
                .orderByDesc("trade_date")
                .last("LIMIT 1");
        StockDaily last = stockDailyMapper.selectOne(queryWrapper);
        return last != null ? last.getTradeDate() : null;
    }
}
