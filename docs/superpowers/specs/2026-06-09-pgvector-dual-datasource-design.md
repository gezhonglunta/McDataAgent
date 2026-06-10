# PgVector 双数据源整合设计

## 1. 背景

当前 `data-agent-management` 模块默认使用 `spring.datasource` 连接主业务数据库，主库为 MySQL，承载以下数据：

- Agent 业务数据
- MyBatis Mapper 读写数据
- 系统初始化与管理数据

现需要将原来的内存向量库 `SimpleVectorStore` 切换为 `PgVectorStore`，并让向量数据落到 PostgreSQL/pgvector 中。

因此系统需要同时连接两个数据库：

- 主业务库：MySQL，使用 `spring.datasource`
- 向量库：PostgreSQL，使用 `spring.vectorstore.datasource`

## 2. 目标

本次整合目标如下：

- 保持主业务库继续使用 MySQL，不影响现有 MyBatis、事务和业务初始化逻辑
- 将 `VectorStore` 的底层实现从 `SimpleVectorStore` 切换为 `PgVectorStore`
- 让 `PgVectorStore` 只使用 PostgreSQL 数据源，不污染主业务数据源
- 兼容当前 Spring Boot 3.4.8、Spring AI 1.1.0 的实际行为

## 3. 约束与已知问题

### 3.1 Spring AI 自动装配不适合当前场景

虽然项目已经引入了 `spring-ai-starter-vector-store-pgvector`，但 Spring AI 的 `PgVectorStoreAutoConfiguration` 默认依赖单一 `DataSource` / `JdbcTemplate` 自动装配。

在双数据源场景下，如果直接开启 `spring.ai.vectorstore.type=pgvector` 并依赖自动装配，会出现两个问题：

- `PgVectorStore` 可能错误地使用主业务数据源
- 当容器中已经存在自定义 `DataSource` Bean 时，Spring Boot 的主库自动装配会被短路

因此本次方案不依赖 Spring AI 内置 `PgVectorStoreAutoConfiguration` 创建最终 Bean，而是手工装配 `PgVectorStore`。

### 3.2 Spring AI 1.1.0 的初始化顺序缺陷

Spring AI 1.1.0 中，`PgVectorStore.afterPropertiesSet()` 的执行顺序为：

1. 如果 `schemaValidation=true`，先校验 schema/table 是否存在
2. 再根据 `initializeSchema=true` 执行建扩展、建 schema、建表、建索引

这意味着在首次启动、目标表尚不存在时：

- 如果配置了 `schema-validation: true`
- 即使同时配置了 `initialize-schema: true`

也会先在校验阶段失败，后续初始化逻辑根本不会执行。

因此不能把“首次建表”完全依赖给 `PgVectorStore.afterPropertiesSet()`。

## 4. 最终方案

### 4.1 数据源划分

系统按职责拆分为两个数据源：

- `dataSource`
  - 绑定 `spring.datasource`
  - 连接 MySQL
  - 标记为 `@Primary`
  - 给 MyBatis、默认 `JdbcTemplate`、事务和主业务逻辑使用

- `vectorStoreDataSource`
  - 绑定 `spring.vectorstore.datasource`
  - 连接 PostgreSQL
  - 仅供 `PgVectorStore` 使用

### 4.2 向量库装配方式

新增独立配置类：

- `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/config/PgVectorStoreConfiguration.java`

配置类职责：

- 显式装配主业务数据源 `dataSource`
- 显式装配向量库数据源 `vectorStoreDataSource`
- 为向量库创建专用 `vectorStoreJdbcTemplate`
- 手工创建 `PgVectorStore` Bean

### 4.3 为什么必须显式补回主数据源

Spring Boot 的 `DataSourceAutoConfiguration` 只有在容器中不存在 `DataSource` Bean 时，才会根据 `spring.datasource` 自动创建主数据源。

如果仅注册 `vectorStoreDataSource`：

- Spring Boot 会认为“容器中已经有 `DataSource`”
- 主库 MySQL 的默认自动装配将不会执行
- MyBatis 最终可能错误地绑定到 PostgreSQL

实际启动中已出现该问题，表现为：

- `AgentMapper` 查询 `agent` 表时落到了 PostgreSQL
- 报错 `relation "agent" does not exist`

因此，必须在 `pgvector` 场景下显式补回主数据源，并将其标记为 `@Primary`。

### 4.4 为什么返回类型必须是 `PgVectorStore`

Spring AI 的 `PgVectorStoreAutoConfiguration` 在创建内置 `vectorStore` Bean 时，判断条件是 `@ConditionalOnMissingBean`。

实际验证表明，仅提供返回类型为 `VectorStore` 的 Bean 不足以稳定阻止其继续创建内置 `PgVectorStore`。

因此自定义配置中直接提供 `PgVectorStore` 类型 Bean，并配合：

- `@ConditionalOnMissingBean(PgVectorStore.class)`

以确保内置自动配置不再介入。

## 5. 初始化策略

### 5.1 首次建表策略

为绕过 Spring AI 1.1.0 中“先校验后初始化”的问题，当前方案在创建 `PgVectorStore` 之前，先手工执行以下初始化：

- `CREATE EXTENSION IF NOT EXISTS vector`
- `CREATE EXTENSION IF NOT EXISTS hstore`
- `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`
- `CREATE SCHEMA IF NOT EXISTS <schema>`
- `CREATE TABLE IF NOT EXISTS <schema>.<table>`
- `CREATE INDEX IF NOT EXISTS <index>`

初始化完成后，再构造 `PgVectorStore` Bean。

这样即便 `PgVectorStore.afterPropertiesSet()` 仍保留内部初始化逻辑，也不会在首次启动时因为表不存在而失败。

### 5.2 schema-validation 配置策略

在首次接入阶段，建议使用：

```yml
spring:
  ai:
    vectorstore:
      pgvector:
        schema-validation: false
        initialize-schema: true
```

原因：

- 首次启动时表可能尚不存在
- Spring AI 1.1.0 的内部顺序会导致 `schema-validation=true` 提前失败

待表已稳定创建后，如确实需要更严格的运行时校验，可考虑再切回 `schema-validation: true`。

## 6. 配置约定

### 6.1 主业务库配置

继续沿用：

```yml
spring:
  datasource:
    url: ...
    username: ...
    password: ...
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 6.2 向量库配置

新增：

```yml
spring:
  vectorstore:
    datasource:
      url: jdbc:postgresql://...
      username: ...
      password: ...
      driver-class-name: org.postgresql.Driver
      type: com.alibaba.druid.pool.DruidDataSource

  ai:
    vectorstore:
      type: pgvector
      pgvector:
        schema-name: dataagent
        table-name: vector_store
        schema-validation: false
        initialize-schema: true
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 2000
        max-document-batch-size: 10000
```

### 6.3 配置键注意事项

以下键名必须使用 Spring AI 期望的 kebab-case：

- `schema-name`
- `table-name`
- `schema-validation`
- `initialize-schema`

错误写法如 `schema-Name` 不会按预期绑定。

## 7. 当前代码落点

本次整合涉及以下文件：

- `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/config/PgVectorStoreConfiguration.java`
- `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/properties/VectorStoreDataSourceProperties.java`
- `data-agent-management/src/main/resources/application-pgvector.yml`

### 7.1 `PgVectorStoreConfiguration`

负责：

- 主数据源显式装配
- 向量库第二数据源显式装配
- 向量库专用 `JdbcTemplate`
- 首次启动的 pgvector schema/table/index 初始化
- `PgVectorStore` Bean 创建

### 7.2 `VectorStoreDataSourceProperties`

负责：

- 将 `spring.vectorstore.datasource` 绑定到独立的 `DataSourceProperties`

避免把向量库连接参数混入主业务数据源。

## 8. 风险与注意事项

### 8.1 PostgreSQL 权限要求

向量库用户需要具备以下能力，至少满足初始化所需权限：

- `CREATE EXTENSION`
- `CREATE SCHEMA`
- `CREATE TABLE`
- `CREATE INDEX`

若权限不足，启动时仍会失败，但失败点将是 PostgreSQL 权限异常，而非 Spring 装配错误。

### 8.2 向量维度必须与 Embedding 模型一致

当前配置中使用：

```yml
dimensions: 1024
```

该值必须与实际 `EmbeddingModel` 输出维度一致。若模型维度不同，将导致向量写入或索引构建异常。

当前项目的 `EmbeddingModel` 是动态代理模型，运行时可切换，因此需要额外注意：

- 运行期切换到不同维度的模型可能破坏已有 pgvector 表结构
- 建议生产环境固定向量模型维度

### 8.3 与 `SimpleVectorStore` 持久化逻辑的关系

项目原先对 `SimpleVectorStore` 做了本地文件持久化和延迟保存切面。

切换到 `PgVectorStore` 后：

- 相关 `SimpleVectorStore` 持久化逻辑会自动失效
- 不需要删除现有逻辑，但不会再参与运行

这是预期行为。

### 8.4 数据迁移问题

原 `SimpleVectorStore` 中若已有本地向量数据，不会自动迁移到 PostgreSQL。

切换后如需保留旧向量，需要额外执行一次数据重建或迁移流程。

## 9. 验证结果

本次整合过程中，已完成以下验证：

- `mvn -pl data-agent-management -DskipTests compile` 编译通过
- 已修复以下两个实际启动问题：
  - Spring AI 内置 `PgVectorStoreAutoConfiguration` 继续生效
  - 主业务 MyBatis 错误绑定到 PostgreSQL

## 10. 后续建议

建议后续补充以下内容：

- 增加启动日志，明确打印主数据源和向量库数据源的目标 URL
- 增加健康检查或启动检查，验证 pgvector 所需扩展是否存在
- 如果后续升级 Spring AI，重新评估是否仍需要手工初始化 schema/table

## 11. 结论

本次整合最终采用的是“显式双数据源 + 手工装配 PgVectorStore + 启动前手工初始化 pgvector schema”的方案。

该方案的核心价值在于：

- 不影响现有 MySQL 主业务链路
- 明确隔离 PostgreSQL 向量库职责
- 绕过 Spring AI 1.1.0 在双数据源和初始化顺序上的实际限制

在当前项目依赖版本下，这是稳定性和可控性都更高的整合方式。
