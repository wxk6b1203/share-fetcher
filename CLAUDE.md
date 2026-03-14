# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

通过HTTP请求调用AKTools服务，拉取AKShare金融数据并存入SQLite数据库，供后续分析使用。

## 常用命令

```bash
# 编译项目
./gradlew compileJava

# 运行测试
./gradlew test

# 打包
./gradlew build

# 在IDE中运行后，通过curl调用API
curl -X POST http://localhost:8090/api/fetch/stock-list
```

## 技术架构

- **Spring Boot 3.5.10** + WebFlux (响应式编程)
- **Mybatis-Plus 3.5.15** (数据持久化)
- **SQLite** (本地数据库)
- **WebClient** (调用AKTools HTTP API)
- **Logback** + SLF4J (日志框架)

## 核心设计

1. **响应式流**: Service层返回`Mono<T>`，Controller层使用`@RestController`处理响应式请求
2. **数据拉取**: 通过WebClient调用AKTools的REST API，解析JSON后存入SQLite
3. **批量处理**: 批量拉取时使用`Thread.sleep(100)`避免请求过快被限流
4. **覆盖逻辑**: stock_basic 重复拉取时会覆盖更新；stock_daily 使用唯一约束避免重复
5. **版本控制**: stock_fundamental 当 mainOperationBusiness/operatingScope/legalRepresentative 变化时新增版本

## 配置 (application-dev.yml)

- 端口: `8090`
- 数据库: `data/fetcher.db`
- AKTools: `http://localhost:8080` (需提前启动)
- 日志级别: INFO

## 数据库表

| 表名 | 说明 |
|------|------|
| stock_basic | 股票基本信息 |
| stock_daily | 日线行情 |
| stock_spot | 实时行情（个股快照） |
| stock_fundamental | 基本面数据（雪球，含版本控制） |

### stock_basic 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(10) | 股票代码 (6位纯数字, 如: 600031, 000776) |
| name | VARCHAR(100) | 股票名称 |
| market | VARCHAR(20) | 交易所市场 (上交所/深交所/北交所) |
| list_date | VARCHAR(10) | 上市日期 (YYYYMMDD) |
| delist_date | VARCHAR(10) | 退市日期 (YYYYMMDD) |
| is_hs | VARCHAR(1) | 是否沪深港通标的 (N/Y) |

### stock_daily 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(10) | 股票代码 (6位纯数字) |
| trade_date | DATE | 交易日期 |
| open | DECIMAL(10,2) | 开盘价 |
| high | DECIMAL(10,2) | 最高价 |
| low | DECIMAL(10,2) | 最低价 |
| close | DECIMAL(10,2) | 收盘价 |
| volume | DECIMAL(20,2) | 成交量 (手) |
| amount | DECIMAL(20,2) | 成交额 (元) |
| outstanding_share | DECIMAL(20,2) | 流通股本 |
| turnover | DECIMAL(10,6) | 涨跌幅 (%) |
| adjust_flag | VARCHAR(4) | 复权类型 (空/qfq/hfq) |

### stock_spot 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(10) | 股票代码 |
| name | VARCHAR(100) | 股票名称 |
| latest_price | DECIMAL(20,2) | 最新价 (元) |
| change_pct | DECIMAL(20,2) | 涨跌幅 (%) |
| change_amount | DECIMAL(20,2) | 涨跌额 (元) |
| volume | DECIMAL(20,2) | 成交量 (手) |
| amount | DECIMAL(20,2) | 成交额 (元) |
| amplitude | DECIMAL(20,2) | 振幅 (%) |
| high/low/open_price/prev_close | DECIMAL(20,2) | 最高/最低/今开/昨收价 |
| volume_ratio | DECIMAL(20,2) | 量比 |
| turnover_rate | DECIMAL(20,2) | 换手率 (%) |
| pe_dynamic | DECIMAL(20,2) | 市盈率-动态 |
| pb | DECIMAL(20,2) | 市净率 |
| total_market_value | DECIMAL(20,2) | 总市值 (元) |
| circ_market_value | DECIMAL(20,2) | 流通市值 (元) |

### stock_fundamental 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(10) | 股票代码 (6位纯数字) |
| org_id | VARCHAR(50) | 组织ID |
| org_name_cn | VARCHAR(200) | 公司中文全称 |
| org_short_name_cn | VARCHAR(100) | 公司中文简称 |
| main_operation_business | TEXT | 主营业务 |
| operating_scope | TEXT | 经营范围 |
| legal_representative | VARCHAR(100) | 法定代表人 |
| reg_asset | DECIMAL(20,2) | 注册资本 (元) |
| staff_num | INTEGER | 员工人数 |
| classi_name | VARCHAR(50) | 企业类型 |
| chairman | VARCHAR(100) | 董事长 |
| provincial_name | VARCHAR(50) | 所在省份 |
| version | INTEGER | 版本号 (从0开始) |

## API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/fetch/stock-list` | POST | 拉取股票列表（覆盖更新） |
| `/api/fetch/spot` | POST | 拉取实时行情 |
| `/api/fetch/daily` | POST | 拉取日线数据 |
| `/api/fetch/fundamental` | POST | 拉取单只股票基本面 |
| `/api/fetch/fundamentals` | POST | 批量拉取所有股票基本面 |
| `/api/stock/with-fundamental` | POST | 查询股票基本信息及关联的基本面 |

## 代码位置

- 配置: `src/main/java/com/github/ak/fetcher/config/AkToolsConfig.java`
- Controller: `src/main/java/com/github/ak/fetcher/controller/StockController.java`
- Service: `src/main/java/com/github/ak/fetcher/service/StockService.java`
- DTO: `src/main/java/com/github/ak/fetcher/dto/`
- 实体/Mappers: `src/main/java/com/github/ak/fetcher/entity/` 和 `mapper/`
- DDL: `src/main/resources/ddl/init.sql`

## 注意事项

- 首次运行需删除`data/fetcher.db`让Spring Boot自动创建表结构
- 雪球基本面接口对于部分股票可能返回null数据
- 批量拉取数据量大时耗时长，建议单独测试每只股票
- stock_fundamental 使用版本控制，当关键字段变化时新增版本
- 日志级别设为 INFO，SQL语句不再打印到控制台
