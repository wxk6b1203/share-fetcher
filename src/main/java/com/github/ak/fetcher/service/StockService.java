package com.github.ak.fetcher.service;

import com.github.ak.fetcher.config.AkToolsConfig;
import com.github.ak.fetcher.dto.StockWithFundamental;
import com.github.ak.fetcher.entity.StockDaily;
import com.github.ak.fetcher.entity.StockBasic;
import com.github.ak.fetcher.entity.StockSpot;
import com.github.ak.fetcher.entity.StockFundamental;
import com.github.ak.fetcher.mapper.StockDailyMapper;
import com.github.ak.fetcher.mapper.StockBasicMapper;
import com.github.ak.fetcher.mapper.StockSpotMapper;
import com.github.ak.fetcher.mapper.StockFundamentalMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockService {

    private final WebClient webClient;
    private final StockDailyMapper stockDailyMapper;
    private final StockBasicMapper stockBasicMapper;
    private final StockSpotMapper stockSpotMapper;
    private final StockFundamentalMapper stockFundamentalMapper;
    private final ObjectMapper objectMapper;

    public StockService(AkToolsConfig akToolsConfig, StockDailyMapper stockDailyMapper,
                       StockBasicMapper stockBasicMapper, StockSpotMapper stockSpotMapper,
                       StockFundamentalMapper stockFundamentalMapper) {
        // 增加缓冲区大小到 10MB，适应大数据量响应
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(akToolsConfig.getBaseUrl() + "/api/public")
                .exchangeStrategies(strategies)
                .build();
        this.stockDailyMapper = stockDailyMapper;
        this.stockBasicMapper = stockBasicMapper;
        this.stockSpotMapper = stockSpotMapper;
        this.stockFundamentalMapper = stockFundamentalMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 拉取股票列表（A股基本信息）
     */
    public Mono<Integer> fetchStockList() {
        return webClient.get()
                .uri("/stock_info_a_code_name")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseAndSaveStockList);
    }

    private int parseAndSaveStockList(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode array = objectMapper.readTree(jsonResponse);
            if (!array.isArray()) {
                return 0;
            }

            int count = 0;
            for (JsonNode node : array) {
                StockBasic stock = new StockBasic();
                String code = node.get("code").asText();
                stock.setCode(code);
                stock.setName(node.get("name").asText());

                if (code.startsWith("sh")) {
                    stock.setMarket("上交所");
                } else if (code.startsWith("sz")) {
                    stock.setMarket("深交所");
                } else if (code.startsWith("bj")) {
                    stock.setMarket("北交所");
                }

                // 覆盖逻辑：先查询是否存在，存在则更新，不存在则插入
                StockBasic existing = stockBasicMapper.selectById(code);
                if (existing != null) {
                    stock.setCreatedAt(existing.getCreatedAt());
                    stockBasicMapper.updateById(stock);
                } else {
                    stockBasicMapper.insert(stock);
                }
                count++;
            }

            return count;
        } catch (Exception e) {
            throw new RuntimeException("解析股票列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拉取股票日线数据
     */
    public Mono<Integer> fetchStockDaily(String symbol, String startDate, String endDate, String adjust) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock_zh_a_hist")
                        .queryParam("symbol", symbol)
                        .queryParam("start_date", startDate)
                        .queryParam("end_date", endDate)
                        .queryParam("adjust", adjust)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseAndSaveDailyData(response, symbol, adjust));
    }

    private int parseAndSaveDailyData(String jsonResponse, String code, String adjustFlag) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            // API直接返回数组，不需要取data字段
            JsonNode data = root.isArray() ? root : root.get("data");

            if (data == null || !data.isArray()) {
                return 0;
            }

            List<StockDaily> list = new ArrayList<>();
            for (JsonNode row : data) {
                StockDaily daily = new StockDaily();
                daily.setCode(extractPureCode(code));
                // 日期格式可能是 "2024-01-02T00:00:00.000"，需要处理
                String dateStr = row.get("日期").asText();
                if (dateStr.contains("T")) {
                    dateStr = dateStr.split("T")[0];
                }
                daily.setTradeDate(LocalDate.parse(dateStr));
                daily.setOpen(getBigDecimal(row, "开盘"));
                daily.setClose(getBigDecimal(row, "收盘"));
                daily.setHigh(getBigDecimal(row, "最高"));
                daily.setLow(getBigDecimal(row, "最低"));
                daily.setVolume(getBigDecimal(row, "成交量"));
                daily.setAmount(getBigDecimal(row, "成交额"));
                daily.setOutstandingShare(getBigDecimal(row, "振幅"));
                daily.setTurnover(getBigDecimal(row, "涨跌幅"));
                daily.setAdjustFlag(adjustFlag);
                list.add(daily);
            }

            for (StockDaily daily : list) {
                stockDailyMapper.insert(daily);
            }

            return list.size();
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
     * 拉取A股实时行情（个股快照）
     */
    public Mono<Integer> fetchStockSpot() {
        return webClient.get()
                .uri("/stock_zh_a_spot_em")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseAndSaveStockSpot);
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
            for (JsonNode node : array) {
                StockSpot spot = new StockSpot();
                spot.setCode(node.get("代码").asText());
                spot.setName(node.get("名称").asText());
                spot.setLatestPrice(getBigDecimal(node, "最新价"));
                spot.setChangePct(getBigDecimal(node, "涨跌幅"));
                spot.setChangeAmount(getBigDecimal(node, "涨跌额"));
                spot.setVolume(getBigDecimal(node, "成交量"));
                spot.setAmount(getBigDecimal(node, "成交额"));
                spot.setAmplitude(getBigDecimal(node, "振幅"));
                spot.setHigh(getBigDecimal(node, "最高"));
                spot.setLow(getBigDecimal(node, "最低"));
                spot.setOpenPrice(getBigDecimal(node, "今开"));
                spot.setPrevClose(getBigDecimal(node, "昨收"));
                spot.setVolumeRatio(getBigDecimal(node, "量比"));
                spot.setTurnoverRate(getBigDecimal(node, "换手率"));
                spot.setPeDynamic(getBigDecimal(node, "市盈率-动态"));
                spot.setPb(getBigDecimal(node, "市净率"));
                spot.setTotalMarketValue(getBigDecimal(node, "总市值"));
                spot.setCircMarketValue(getBigDecimal(node, "流通市值"));
                spot.setSpeedPct(getBigDecimal(node, "涨速"));
                spot.setChange5minPct(getBigDecimal(node, "5分钟涨跌"));
                spot.setChange60dPct(getBigDecimal(node, "60日涨跌幅"));
                spot.setChangeYtdPct(getBigDecimal(node, "年初至今涨跌幅"));
                spot.setTradeTime(LocalDateTime.now());

                stockSpotMapper.insert(spot);
                count++;
            }

            return count;
        } catch (Exception e) {
            throw new RuntimeException("解析实时行情失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拉取单只股票基本面数据
     */
    public Mono<Integer> fetchStockFundamental(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock_individual_basic_info_xq")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseAndSaveStockFundamental(response, symbol));
    }

    /**
     * 批量拉取股票基本面数据
     */
    public Mono<Integer> fetchAllStockFundamentals() {
        return webClient.get()
                .uri("/stock_info_a_code_name")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::fetchFundamentalsForAllStocks);
    }

    private int fetchFundamentalsForAllStocks(String jsonResponse) {
        try {
            JsonNode array = objectMapper.readTree(jsonResponse);
            if (!array.isArray()) {
                return 0;
            }

            int count = 0;
            for (JsonNode node : array) {
                String code = node.get("code").asText();
                // 转换代码格式: 000001 -> SH000001, 000002 -> SZ000002
                String symbol = convertToSymbol(code);
                try {
                    int result = fetchStockFundamental(symbol).block();
                    count += result;
                    // 添加延迟避免请求过快
                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("拉取 " + code + " 失败: " + e.getMessage());
                }
            }
            return count;
        } catch (Exception e) {
            throw new RuntimeException("批量拉取基本面数据失败: " + e.getMessage(), e);
        }
    }

    private String convertToSymbol(String code) {
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            return code.toUpperCase();
        }
        if (code.startsWith("6")) {
            return "SH" + code;
        } else if (code.startsWith("0") || code.startsWith("3")) {
            return "SZ" + code;
        } else if (code.startsWith("8") || code.startsWith("4")) {
            return "BJ" + code;
        }
        return code;
    }

    private int parseAndSaveStockFundamental(String jsonResponse, String symbol) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return 0;
        }

        try {
            JsonNode array = objectMapper.readTree(jsonResponse);
            if (!array.isArray()) {
                return 0;
            }

            StockFundamental fundamental = new StockFundamental();
            // 入库时去掉前缀，只保留6位纯数字代码
            fundamental.setCode(extractPureCode(symbol));

            for (JsonNode node : array) {
                String item = node.get("item").asText();
                String value = node.get("value").asText();

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
                        fundamental.setRegAsset(new BigDecimal(value));
                        break;
                    case "staff_num":
                        fundamental.setStaffNum(Integer.parseInt(value));
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
                        fundamental.setExecutivesNums(Integer.parseInt(value));
                        break;
                    case "actual_issue_vol":
                        fundamental.setActualIssueVol(new BigDecimal(value));
                        break;
                    case "issue_price":
                        fundamental.setIssuePrice(new BigDecimal(value));
                        break;
                    case "actual_rc_net_amt":
                        fundamental.setActualRcNetAmt(new BigDecimal(value));
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

            // 版本控制：检查关键字段是否有变化
            String code = fundamental.getCode();
            StockFundamental latest = stockFundamentalMapper.selectLatestByCode(code);

            boolean hasChanged = false;
            if (latest == null) {
                // 首次插入，版本为1
                fundamental.setVersion(0);
                hasChanged = true;
            } else {
                // 检查关键字段是否变化
                boolean mainBizChanged = !equalsOrBothNull(latest.getMainOperationBusiness(), fundamental.getMainOperationBusiness());
                boolean scopeChanged = !equalsOrBothNull(latest.getOperatingScope(), fundamental.getOperatingScope());
                boolean legalRepChanged = !equalsOrBothNull(latest.getLegalRepresentative(), fundamental.getLegalRepresentative());

                hasChanged = mainBizChanged || scopeChanged || legalRepChanged;

                if (hasChanged) {
                    // 版本+1
                    fundamental.setVersion(latest.getVersion() + 1);
                } else {
                    // 关键字段未变化，跳过
                    System.out.println("股票 " + code + " 基本面数据无变化，跳过插入");
                    return 0;
                }
            }

            stockFundamentalMapper.insert(fundamental);
            return 1;
        } catch (Exception e) {
            throw new RuntimeException("解析基本面数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 比较两个字符串是否相等（同时为null也返回true）
     */
    private boolean equalsOrBothNull(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * 根据股票代码查询关联的基本面数据
     * @param code 股票代码（如 000111, 600000, 或带前缀的 sh000111）
     * @return 关联后的股票信息和基本面数据
     */
    public Mono<StockWithFundamental> getStockWithFundamental(String code) {
        if (code == null || code.isEmpty()) {
            return Mono.empty();
        }

        // 提取纯数字代码用于关联查询
        String pureCode = extractPureCode(code);
        if (pureCode == null || pureCode.isEmpty()) {
            return Mono.empty();
        }

        // 查询basic表和fundamental表，都使用纯数字6位代码
        StockBasic basic = stockBasicMapper.selectById(pureCode);
        StockFundamental fundamental = stockFundamentalMapper.selectByCode(pureCode);

        if (basic == null && fundamental == null) {
            return Mono.empty();
        }

        return Mono.just(new StockWithFundamental(basic, fundamental));
    }

    /**
     * 从带前缀的代码中提取纯数字代码
     * sh600000 -> 600000, SZ000776 -> 000776
     */
    public String extractPureCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        // 去掉 sh/SZ/bj 等前缀（不区分大小写）
        if (code.length() > 2) {
            String prefix = code.substring(0, 2).toLowerCase();
            if ("sh".equals(prefix) || "sz".equals(prefix) || "bj".equals(prefix)) {
                return code.substring(2);
            }
        }
        return code;
    }
}
