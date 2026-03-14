# Stock Data Fetcher

通过HTTP请求调用AKTools服务，拉取AKShare金融数据并存入SQLite数据库，供后续分析使用。

## 功能特性

- 拉取A股股票列表（基本信息）
- 拉取A股实时行情（个股快照）
- 拉取A股日线行情数据
- 拉取股票基本面数据（雪球）
- 异步任务系统，支持任务取消和进度追踪

## 技术栈

- **Spring Boot 3.5.10** + WebFlux (响应式编程)
- **Mybatis-Plus 3.5.15** (数据持久化)
- **SQLite** (本地数据库)
- **WebClient** (调用AKTools HTTP API)

## 快速开始

### 1. 启动AKTools服务

需要先启动AKTools HTTP服务，默认地址：`http://localhost:8080`

```bash
# 使用Docker
docker run -d -p 8080:8080 akshare/aktools

# 或使用Python
pip install akshare
python -m akshare
```

### 2. 编译运行

```bash
# 编译项目
./gradlew build

# 运行
./gradlew bootRun
```

服务启动后访问 `http://localhost:8090`

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

### 任务类型

- `HISTORY_DATA_FETCH`: 历史行情数据拉取任务
- `FUNDAMENTAL_FETCH`: 基本面数据拉取任务
- `SPOT_FETCH`: 实时行情数据拉取任务

## 使用示例

```bash
# 拉取股票列表
curl -X POST http://localhost:8090/api/fetch/stock-list

# 拉取实时行情
curl -X POST http://localhost:8090/api/fetch/spot

# 提交历史数据拉取任务
curl -X POST http://localhost:8090/api/task/submit/HISTORY_DATA_FETCH \
  -H "Content-Type: application/json" \
  -d '{"startDate": "2024-01-01", "endDate": "2024-12-31"}'

# 查询任务状态
curl http://localhost:8090/api/task/status/1
```

## 数据库表

| 表名 | 说明 |
|------|------|
| stock_basic | 股票基本信息 |
| stock_daily | 日线行情 |
| stock_spot | 实时行情（个股快照） |
| stock_fundamental | 基本面数据（雪球，含版本控制） |
| task | 异步任务表 |

## 目录结构

```
src/main/java/com/github/ak/fetcher/
├── config/          # 配置类
├── controller/      # 控制器
├── service/        # 业务逻辑
├── entity/         # 实体类
├── mapper/         # 数据访问层
├── dto/            # 数据传输对象
└── util/           # 工具类
```

## License

AGPL 3.0
