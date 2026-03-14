## AK拉取器
本项目的目的是通过HTTP请求调用AKTOOLS，拉取AKShare数据，并将数据存储在SQLite中，供后续分析使用。本项目使用Mybatis-Plus来简化数据库操作，使用Spring Boot来搭建HTTP服务。

### 项目结构
本项目使用Gradle进行构建。你需要读取各个配置来知晓如何使用。

src/ Java等代码目录

akshare/ AKShare子项目，调用AKTools时可以从这个项目的
```text
docs/data/
```
查到数据字典

docs/ AKTools简介

本项目的运行环境在localhost:8080下有一个AKTools服务可以直接调用，如果请求失败请告诉我。

### 所需工作
1. 完善依赖，包括特定的SQLite驱动等依赖。
2. 了解AKTools用法，AKShare用法，根据AKShare的数据字典，设计数据库表结构，并使用Mybatis-Plus生成实体类和Mapper接口。把表结构的SQL语句放在docs/ddl/目录下。
3. 编写Service类，调用AKTools的HTTP API来拉取数据，并将数据存储到SQLite数据库中。
4. 编写Controller类，提供HTTP接口来触发数据拉取和查询操作。
5. 编写单元测试，确保各个组件的功能正确。

注意：使用fetch工具时，返回可能被截断，需要注意数据的完整性和正确性。