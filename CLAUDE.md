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
| task | 异步任务表 |

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
| bid | DECIMAL(20,2) | 买入价 (元) |
| ask | DECIMAL(20,2) | 卖出价 (元) |
| volume | DECIMAL(20,2) | 成交量 (手) |
| amount | DECIMAL(20,2) | 成交额 (元) |
| high/low/open_price/prev_close | DECIMAL(20,2) | 最高/最低/今开/昨收价 |
| timestamp | VARCHAR(10) | 时间戳 (HH:mm:ss) |

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

### task 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键 |
| task_type | VARCHAR(50) | 任务类型 |
| input_params | TEXT | 输入参数(JSON格式) |
| status | VARCHAR(20) | 任务状态 (PENDING/RUNNING/COMPLETED/FAILED/CANCELLED) |
| result | TEXT | 执行结果(JSON格式) |
| created_at | TIMESTAMP | 创建时间 |
| started_at | TIMESTAMP | 开始执行时间 |
| finished_at | TIMESTAMP | 结束时间 |

## API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/fetch/stock-list` | POST | 拉取股票列表（覆盖更新） |
| `/api/fetch/spot` | POST | 拉取实时行情 |
| `/api/fetch/daily` | POST | 拉取日线数据 |
| `/api/fetch/fundamental` | POST | 拉取单只股票基本面 |
| `/api/fetch/fundamentals` | POST | 批量拉取所有股票基本面 |
| `/api/stock/with-fundamental` | POST | 查询股票基本信息及关联的基本面 |

### 任务系统接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/task/submit/{taskType}` | POST | 提交异步任务 |
| `/api/task/status/{taskId}` | GET | 查询任务状态 |
| `/api/task/cancel/{taskId}` | POST | 停止指定任务 |
| `/api/task/list` | GET | 分页获取任务列表 |

#### 任务类型

- `HISTORY_DATA_FETCH`: 历史行情数据拉取任务
  - 参数: `startDate`, `endDate`, `symbol`(可选，单只股票), `codeStart`(可选，代码范围开始), `codeEnd`(可选，代码范围结束), `adjust`(复权类型，默认qfq)
- `FUNDAMENTAL_FETCH`: 基本面数据拉取任务
  - 参数: `symbol`(可选，单只股票), `codeStart`(可选，代码范围开始), `codeEnd`(可选，代码范围结束)
- `SPOT_FETCH`: 实时行情数据拉取任务
  - 参数: 无（获取全部A股实时行情）

## 代码位置

- 配置: `src/main/java/com/github/ak/fetcher/config/`
  - AkToolsConfig.java - AKTools配置
  - TaskConfig.java - 任务处理器注册
- Controller: `src/main/java/com/github/ak/fetcher/controller/`
  - StockController.java - 股票数据接口
  - TaskController.java - 任务管理接口
- Service: `src/main/java/com/github/ak/fetcher/service/`
  - StockService.java - 股票数据服务
  - TaskExecutor.java - 任务执行器
  - HistoryDataFetchTask.java - 历史数据拉取任务
  - FundamentalFetchTask.java - 基本面数据拉取任务
- DTO: `src/main/java/com/github/ak/fetcher/dto/`
- 实体/Mappers: `src/main/java/com/github/ak/fetcher/entity/` 和 `mapper/`
- DDL: `src/main/resources/ddl/init.sql`

## 注意事项

- 首次运行需删除`data/fetcher.db`让Spring Boot自动创建表结构
- 雪球基本面接口对于部分股票可能返回null数据
- 批量拉取数据量大时耗时长，建议单独测试每只股票
- stock_fundamental 使用版本控制，当关键字段变化时新增版本
- 日志级别设为 INFO，SQL语句不再打印到控制台
- 历史行情拉取支持增量更新：如果数据库已有数据，会自动跳过已存在的日期范围
- 任务系统使用Java虚拟线程实现，支持任务取消和进度追踪

## 定时任务

| 任务 | 执行时间 | 说明 |
|------|----------|------|
| DailyHistoryFetchScheduler | 每天 4:00 | 拉取当天所有股票的历史行情 |

定时任务通过 Spring `@Scheduled` 实现，每天凌晨4点自动触发，通过任务系统提交 `HISTORY_DATA_FETCH` 任务拉取当天行情数据。
