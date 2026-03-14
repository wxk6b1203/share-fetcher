package com.github.ak.fetcher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ak.fetcher.config.AkToolsConfig;
import com.github.ak.fetcher.entity.StockBasic;
import com.github.ak.fetcher.entity.StockFundamental;
import com.github.ak.fetcher.mapper.StockBasicMapper;
import com.github.ak.fetcher.mapper.StockFundamentalMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class FundamentalFetchTask implements TaskExecutor.CancellableTask {
    private static final Logger log = LoggerFactory.getLogger(FundamentalFetchTask.class);

    private final StockBasicMapper stockBasicMapper;
    private final StockFundamentalMapper stockFundamentalMapper;
    private final TaskExecutor taskExecutor;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 用于检查任务是否被取消
    private volatile boolean cancelled = false;

    public FundamentalFetchTask(StockBasicMapper stockBasicMapper,
                               StockFundamentalMapper stockFundamentalMapper,
                               TaskExecutor taskExecutor,
                               AkToolsConfig akToolsConfig,
                               ObjectMapper objectMapper) {
        this.stockBasicMapper = stockBasicMapper;
        this.stockFundamentalMapper = stockFundamentalMapper;
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
            JsonNode params = objectMapper.readTree(inputParams);

            String symbol = params.has("symbol") && !params.get("symbol").isNull() ? params.get("symbol").asText() : null;
            String codeStart = params.has("codeStart") && !params.get("codeStart").isNull() ? params.get("codeStart").asText() : null;
            String codeEnd = params.has("codeEnd") && !params.get("codeEnd").isNull() ? params.get("codeEnd").asText() : null;

            log.info("Executing fundamental fetch task: symbol={}, codeStart={}, codeEnd={}",
                    symbol, codeStart, codeEnd);

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

            log.info("Will fetch fundamental data for {} symbols", symbols.size());

            int totalInserted = 0;
            int processedCount = 0;
            int totalSymbols = symbols.size();
            int skippedCount = 0;
            int failedCount = 0;

            for (String code : symbols) {
                // 检查任务是否被取消
                if (cancelled) {
                    log.info("Task cancelled, stopping at code: {}", code);
                    break;
                }

                try {
                    // 添加延迟避免请求过快（1-3秒随机）
                    Thread.sleep((long) (Math.random() * 1000 + 500));

                    // 检查是否已存在最新版本的基本面数据
                    QueryWrapper<StockFundamental> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("code", code)
                            .orderByDesc("version")
                            .last("LIMIT 1");
                    StockFundamental existing = stockFundamentalMapper.selectOne(queryWrapper);

                    if (existing != null) {
                        log.info("Fundamental data already exists for {}, skipping", code);
                        skippedCount++;
                        processedCount++;
                        updateProgress(processedCount, totalSymbols, totalInserted, skippedCount, failedCount);
                        continue;
                    }

                    log.info("Fetching fundamental data for {}", code);
                    int inserted = fetchAndSaveFundamentalData(code);
                    if (inserted > 0) {
                        totalInserted += inserted;
                        log.info("Fetched fundamental data for {}: {} records inserted", code, inserted);
                    } else {
                        log.info("No fundamental data returned for {}", code);
                        failedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to fetch fundamental data for {}: {}", code, e.getMessage());
                    failedCount++;
                }

                processedCount++;
                updateProgress(processedCount, totalSymbols, totalInserted, skippedCount, failedCount);
            }

            return String.format("{\"totalInserted\": %d, \"symbolsProcessed\": %d, \"totalSymbols\": %d, \"skippedCount\": %d, \"failedCount\": %d, \"progress\": \"%d/%d\"}",
                    totalInserted, processedCount, totalSymbols, skippedCount, failedCount, processedCount, totalSymbols);
        } catch (Exception e) {
            log.error("Fundamental fetch task failed", e);
            throw new RuntimeException("Task execution failed: " + e.getMessage(), e);
        }
    }

    private int fetchAndSaveFundamentalData(String code) {
        // 添加市场前缀：6开头是上海，0、3开头是深圳，9开头是北交所
        String marketSymbol = code.startsWith("6") ? "sh" + code :
                            code.startsWith("9") ? "bj" + code : "sz" + code;

        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock_individual_basic_info_xq")
                        .queryParam("symbol", marketSymbol)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return parseAndSaveFundamentalData(response, code);
    }

    private int parseAndSaveFundamentalData(String jsonResponse, String code) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode array = objectMapper.readTree(jsonResponse);
            if (!array.isArray()) {
                return 0;
            }

            StockFundamental fundamental = new StockFundamental();
            fundamental.setCode(code);

            for (JsonNode node : array) {
                String item = node.get("item").asText();
                JsonNode valueNode = node.get("value");
                String value = valueNode == null || valueNode.isNull() ? null : valueNode.isTextual() ? valueNode.asText() : valueNode.toString();

                switch (item) {
                    case "org_id":
                        fundamental.setOrgId(value);
                        break;
                    case "org_name_cn":
                        fundamental.setOrgNameCn(value);
                        break;
                    case "org_short_name_cn":
                        fundamental.setOrgShortNameCn(value);
                        break;
                    case "org_name_en":
                        fundamental.setOrgNameEn(value);
                        break;
                    case "org_short_name_en":
                        fundamental.setOrgShortNameEn(value);
                        break;
                    case "main_operation_business":
                        fundamental.setMainOperationBusiness(value);
                        break;
                    case "operating_scope":
                        fundamental.setOperatingScope(value);
                        break;
                    case "district_encode":
                        fundamental.setDistrictEncode(value);
                        break;
                    case "org_cn_introduction":
                        fundamental.setOrgCnIntroduction(value);
                        break;
                    case "legal_representative":
                        fundamental.setLegalRepresentative(value);
                        break;
                    case "general_manager":
                        fundamental.setGeneralManager(value);
                        break;
                    case "secretary":
                        fundamental.setSecretary(value);
                        break;
                    case "established_date":
                        fundamental.setEstablishedDate(value);
                        break;
                    case "reg_asset":
                        if (value != null) fundamental.setRegAsset(new BigDecimal(value));
                        break;
                    case "staff_num":
                        if (value != null) fundamental.setStaffNum(Integer.parseInt(value));
                        break;
                    case "telephone":
                        fundamental.setTelephone(value);
                        break;
                    case "postcode":
                        fundamental.setPostcode(value);
                        break;
                    case "fax":
                        fundamental.setFax(value);
                        break;
                    case "email":
                        fundamental.setEmail(value);
                        break;
                    case "org_website":
                        fundamental.setOrgWebsite(value);
                        break;
                    case "reg_address_cn":
                        fundamental.setRegAddressCn(value);
                        break;
                    case "office_address_cn":
                        fundamental.setOfficeAddressCn(value);
                        break;
                    case "currency":
                        fundamental.setCurrency(value);
                        break;
                    case "listed_date":
                        fundamental.setListedDate(value);
                        break;
                    case "provincial_name":
                        fundamental.setProvincialName(value);
                        break;
                    case "actual_controller":
                        fundamental.setActualController(value);
                        break;
                    case "classi_name":
                        fundamental.setClassiName(value);
                        break;
                    case "pre_name_cn":
                        fundamental.setPreNameCn(value);
                        break;
                    case "chairman":
                        fundamental.setChairman(value);
                        break;
                    case "executives_nums":
                        if (value != null) fundamental.setExecutivesNums(Integer.parseInt(value));
                        break;
                    case "actual_issue_vol":
                        if (value != null) fundamental.setActualIssueVol(new BigDecimal(value));
                        break;
                    case "issue_price":
                        if (value != null) fundamental.setIssuePrice(new BigDecimal(value));
                        break;
                    case "actual_rc_net_amt":
                        if (value != null) fundamental.setActualRcNetAmt(new BigDecimal(value));
                        break;
                    case "affiliate_industry":
                        fundamental.setAffiliateIndustry(value);
                        break;
                }
            }

            // 如果公司名称为空，说明没有基本面数据
            if (fundamental.getOrgNameCn() == null || fundamental.getOrgNameCn().isEmpty()) {
                return 0;
            }

            // 检查是否需要新增版本（当关键字段变化时）
            int newVersion = getNextVersion(code);
            fundamental.setVersion(newVersion);

            stockFundamentalMapper.insert(fundamental);
            return 1;
        } catch (Exception e) {
            log.error("解析基本面数据失败 for {}: {}", code, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取下一个版本号
     */
    private int getNextVersion(String code) {
        QueryWrapper<StockFundamental> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("code", code)
                .orderByDesc("version")
                .last("LIMIT 1");
        StockFundamental existing = stockFundamentalMapper.selectOne(queryWrapper);
        return existing != null ? existing.getVersion() + 1 : 0;
    }

    /**
     * 更新任务进度
     */
    private void updateProgress(int processed, int total, int inserted, int skipped, int failed) {
        String progress = String.format("{\"processed\": %d, \"total\": %d, \"inserted\": %d, \"skipped\": %d, \"failed\": %d, \"progress\": \"%d/%d (%.1f%%)\"}",
                processed, total, inserted, skipped, failed, processed, total, (processed * 100.0 / total));
        taskExecutor.updateCurrentTaskProgress(progress);
        log.info("Task progress: {}/{} ({:.1f}%)", processed, total, processed * 100.0 / total);
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
