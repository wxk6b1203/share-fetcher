package com.github.ak.fetcher.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ak.fetcher.config.AkToolsConfig;
import com.github.ak.fetcher.entity.StockSpot;
import com.github.ak.fetcher.mapper.StockSpotMapper;
import com.github.ak.fetcher.util.StockCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class SpotFetchTask implements TaskExecutor.CancellableTask {
    private static final Logger log = LoggerFactory.getLogger(SpotFetchTask.class);

    private final StockSpotMapper stockSpotMapper;
    private final TaskExecutor taskExecutor;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 用于检查任务是否被取消
    private volatile boolean cancelled = false;

    public SpotFetchTask(StockSpotMapper stockSpotMapper,
                        TaskExecutor taskExecutor,
                        AkToolsConfig akToolsConfig,
                        ObjectMapper objectMapper) {
        this.stockSpotMapper = stockSpotMapper;
        this.taskExecutor = taskExecutor;
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
            log.info("Executing spot fetch task");

            // 调用API获取实时行情
            String response = webClient.get()
                    .uri("/stock_zh_a_spot")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            int count = parseAndSaveStockSpot(response);

            log.info("Spot fetch task completed: {} records saved", count);
            return String.format("{\"count\": %d}", count);
        } catch (Exception e) {
            log.error("Spot fetch task failed", e);
            throw new RuntimeException("Task execution failed: " + e.getMessage(), e);
        }
    }

    private int parseAndSaveStockSpot(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode array = objectMapper.readTree(jsonResponse);
            if (!array.isArray()) {
                return 0;
            }

            int count = 0;
            int skipped = 0;
            int total = array.size();

            for (JsonNode node : array) {
                // 检查任务是否被取消
                if (cancelled) {
                    log.info("Task cancelled, stopping at record: {}", count);
                    break;
                }

                String code = node.get("代码").asText();

                // 检查是否已存在，跳过已存在的记录 (code + timestamp 唯一)
                String pureCode = StockCodeUtil.extractPureCode(code);
                String timestamp = node.has("时间戳") ? node.get("时间戳").asText() : null;
                QueryWrapper<StockSpot> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("code", pureCode).eq("timestamp", timestamp);
                StockSpot existing = stockSpotMapper.selectOne(queryWrapper);
                if (existing != null) {
                    skipped++;
                    continue;
                }

                StockSpot spot = new StockSpot();
                spot.setCode(pureCode);
                spot.setName(node.get("名称").asText());
                spot.setLatestPrice(getBigDecimal(node, "最新价"));
                spot.setChangePct(getBigDecimal(node, "涨跌幅"));
                spot.setChangeAmount(getBigDecimal(node, "涨跌额"));
                spot.setBid(getBigDecimal(node, "买入"));
                spot.setAsk(getBigDecimal(node, "卖出"));
                spot.setVolume(getBigDecimal(node, "成交量"));
                spot.setAmount(getBigDecimal(node, "成交额"));
                spot.setHigh(getBigDecimal(node, "最高"));
                spot.setLow(getBigDecimal(node, "最低"));
                spot.setOpenPrice(getBigDecimal(node, "今开"));
                spot.setPrevClose(getBigDecimal(node, "昨收"));
                spot.setTimestamp(node.has("时间戳") ? node.get("时间戳").asText() : null);
                spot.setCreatedAt(LocalDateTime.now());

                stockSpotMapper.insert(spot);
                count++;

                // 每处理100条更新一次进度
                if (count % 100 == 0) {
                    updateProgress(count, total, skipped);
                }
            }

            updateProgress(count, total, skipped);
            log.info("Stock spot saved: {}, skipped: {}", count, skipped);
            return count;
        } catch (Exception e) {
            log.error("解析实时行情失败: {}", e.getMessage());
            throw new RuntimeException("解析实时行情失败: " + e.getMessage(), e);
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
     * 更新任务进度
     */
    private void updateProgress(int processed, int total, int skipped) {
        String progress = String.format("{\"processed\": %d, \"total\": %d, \"skipped\": %d, \"progress\": \"%d/%d (%.1f%%)\"}",
                processed, total, skipped, processed, total, (processed * 100.0 / total));
        taskExecutor.updateCurrentTaskProgress(progress);
    }

    /**
     * 设置取消标志
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }

}
