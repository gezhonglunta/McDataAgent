# SSO单点登录与会话用户隔离 - 设计规格

## 概述

为 DataAgent 系统新增 SSO 单点登录功能和会话用户隔离能力。系统挂载在 Spring Cloud Gateway 下，通过网关转发的 JWT Token 实现用户认证，按用户维度隔离会话数据。同时支持 URL 前缀配置和前后端一体化打包。

## 设计原则

- **最小侵入**：新增代码集中在新类中，原有代码仅做轻量调用
- **向后兼容**：所有功能通过配置开关控制，未配置时行为与原版完全一致
- **响应式优先**：基于 WebFlux Reactor Context 传递用户信息，符合项目技术栈

## 架构设计

### 方案选择：WebFilter + Reactor Context

选择理由：
1. 完全符合 WebFlux 响应式编程范式
2. 新增代码集中在 Filter + 工具类，原有控制器只需少量调用
3. 通过 `@ConditionalOnProperty` 控制启用，不影响现有功能
4. 不引入 Spring Security 等额外框架依赖

### 整体流程

```
请求 → Spring Cloud Gateway (JWT验签)
     → /nl2sql/ 前缀转发到本系统
     → JwtAuthenticationWebFilter (解析JWT, 提取userId)
     → Reactor Context 存储 JwtUser
     → Controller 通过 UserContextHolder 获取用户信息
     → Service 层按 userId 过滤数据
```

## 后端设计

### 新增类

| 类 | 包路径 | 职责 |
|---|---|---|
| `JwtUser` | `dto/` | 用户信息 DTO，包含 `userId(String)` |
| `UserContextHolder` | `util/` | 用户信息存取工具，提供 `Mono<JwtUser> getCurrentUser()`（Reactor Context）和 `String getCurrentUserId()`（ThreadLocal 兜底） |
| `JwtAuthenticationWebFilter` | `filter/` | WebFilter，解析 JWT header，将 JwtUser 同时写入 Reactor Context 和 ThreadLocal |
| `SpaWebFilter` | `filter/` | SPA History 模式 fallback，前端路由返回 index.html |

### JwtAuthenticationWebFilter 逻辑

```
请求进入 → 检查 Authorization header
  → 有 Bearer token → Base64 decode payload（不验签，网关已验签）
    → 提取 sub 字段作为 userId → 构建 JwtUser
  → 无 token → userId = null（兼容无用户模式）
  → 将 JwtUser 同时写入 Reactor Context 和 ThreadLocal
  → 继续 filter chain
  → 请求结束时清理 ThreadLocal（防止线程池复用导致数据泄漏）
```

**ThreadLocal 生命周期**：Filter 在请求开始时设置 ThreadLocal，在 `doFinally` 中清理，确保线程池场景下不会泄漏。

### 配置项

```yaml
spring.ai.alibaba.data-agent:
  auth:
    enabled: false          # 是否启用认证，默认关闭
    jwt-claim-name: sub     # JWT 中用户ID的 claim 名称
  base-path: ""             # URL 前缀，空字符串表示无前缀
```

`base-path` 在应用启动时映射到 `spring.webflux.base-path`，由 Spring WebFlux 自动处理所有请求路径前缀。

### 数据库变更

`chat_session` 表 `user_id` 字段类型变更：

```sql
-- 原
user_id BIGINT COMMENT '用户ID',

-- 新
user_id VARCHAR(33) COMMENT '用户ID',
```

同步修改 `schema.sql` 和 `application-h2.yml` 中的建表语句。

### ChatSession 实体变更

```java
// 原
private Long userId;

// 新
private String userId;
```

### ChatSessionService 接口变更

```java
// 原
ChatSession createSession(Integer agentId, String title, Long userId);
List<ChatSession> findByAgentId(Integer agentId);
void clearSessionsByAgentId(Integer agentId);

// 新
ChatSession createSession(Integer agentId, String title, String userId);
List<ChatSession> findByAgentId(Integer agentId, String userId);
void clearSessionsByAgentId(Integer agentId, String userId);

// 保留默认方法向后兼容
default List<ChatSession> findByAgentId(Integer agentId) {
    return findByAgentId(agentId, null);
}
default void clearSessionsByAgentId(Integer agentId) {
    clearSessionsByAgentId(agentId, null);
}
```

### ChatSessionMapper 变更

查询增加可选的 `user_id` 条件：

```sql
-- findByAgentId
SELECT * FROM chat_session
WHERE agent_id = #{agentId} AND status != 'deleted'
<if test="userId != null"> AND user_id = #{userId} </if>
ORDER BY is_pinned DESC, update_time DESC

-- clearSessionsByAgentId
UPDATE chat_session SET status = 'deleted'
WHERE agent_id = #{agentId}
<if test="userId != null"> AND user_id = #{userId} </if>
```

### ChatController 变更

通过 `UserContextHolder.getCurrentUserId()` 获取 userId（ThreadLocal 方式，由 Filter 在请求开始时设置，请求结束时清理），不改变方法签名和返回类型：

```java
@GetMapping("/agent/{id}/sessions")
public ResponseEntity<List<ChatSession>> getAgentSessions(@PathVariable Integer id) {
    String userId = UserContextHolder.getCurrentUserId(); // null when auth disabled
    List<ChatSession> sessions = chatSessionService.findByAgentId(id, userId);
    return ResponseEntity.ok(sessions);
}
```

`createSession` 和 `clearAgentSessions` 同理，仅增加一行获取 userId。

### SSE 用户隔离（SessionEventController / SessionEventPublisher）

- Sink key 从 `agentId` 改为 `agentId + ":" + userId`
- 无用户模式下 key 为 `agentId + ":"`（空字符串），保持兼容
- 注册/注销/推送均按 `agentId + userId` 维度操作

### 向后兼容策略

- `userId` 为 `null` 时查询不过滤用户（兼容无用户模式）
- `auth.enabled=false` 时 Controller 层传入 `userId=null`，行为与原来完全一致
- 原有单参数方法保留为 default 方法

## 前端设计

### URL 前缀配置

**环境变量**：

```bash
# .env.development
VITE_FRONT_BASE=/
VITE_API_BASE=

# .env.production
VITE_FRONT_BASE=/front/
VITE_API_BASE=/nl2sql
```

**Vite 配置**：

```js
const FRONT_BASE = process.env.VITE_FRONT_BASE || '/'
export default {
  base: FRONT_BASE,
}
```

**Vue Router**：

```js
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})
```

### API 请求改造

**Axios 拦截器**（`services/common.ts`）：

```ts
axiosInstance.interceptors.request.use(config => {
  const match = document.cookie.match(/Bearer=([^;]+)/)
  if (match) {
    config.headers.Authorization = `Bearer ${match[1]}`
  }
  return config
})
```

从 cookie 读取 Bearer token，读取不到则不加入 Authorization header。

### 新增用户级路由

```js
{
  path: '/user-agent/:id/run',
  name: 'UserAgentRun',
  component: () => import('@/views/AgentRun.vue'),
  meta: { hideHeader: true }
}
```

### BaseLayout 改造

根据当前路由 `meta.hideHeader` 隐藏 header：

```vue
<header v-if="!route.meta.hideHeader" class="app-header">
  <!-- 原有导航栏 -->
</header>
<main :class="{ 'full-height': route.meta.hideHeader }">
  <slot />
</main>
```

### AgentRun.vue

无需修改核心逻辑，后端根据 JWT 自动隔离会话。

## 打包配置

### Maven 前后端一体化打包

在 `data-agent-management/pom.xml` 中新增：

1. `frontend-maven-plugin`：执行 `npm install` + `npm run build`
2. `maven-resources-plugin`：将前端 `dist/` 产物复制到 `classpath:/static/front/`

### Spring Boot 静态资源

`WebConfig.java` 新增前端静态资源映射：

```java
registry.addResourceHandler(basePath + "/front/**")
        .addResourceLocations("classpath:/static/front/");
```

### SPA History 模式 Fallback

`SpaWebFilter` 拦截前端路由路径（非 API、非静态资源），返回 `index.html`。

## 部署场景矩阵

| 场景 | base-path | auth.enabled | 行为 |
|------|-----------|-------------|------|
| 开发环境 | `""` | `false` | 完全兼容原有行为 |
| 生产环境（网关下） | `/nl2sql` | `true` | SSO + 用户隔离 |
| 独立部署 | `""` | `true` | 有认证但无前缀 |

## 对源项目的影响

| 修改类型 | 文件 | 影响程度 |
|---------|------|---------|
| 新增 | `filter/`, `dto/JwtUser`, `util/UserContextHolder` | 零侵入 |
| 新增 | `SpaWebFilter`, Maven 打包配置 | 零侵入 |
| 新增 | 前端 `.env.*` 文件、路由条目、Axios 拦截器 | 零侵入 |
| 修改 | `ChatController` (3处调用 UserContextHolder) | 轻微 |
| 修改 | `SessionEventController` / `SessionEventPublisher` | 轻微 |
| 修改 | `ChatSessionService` 接口签名（有 default 方法兜底） | 中等 |
| 修改 | `ChatSessionMapper` SQL | 轻微 |
| 修改 | `schema.sql` (1个字段类型) | 轻微 |
| 修改 | `BaseLayout.vue` (条件隐藏 header) | 轻微 |
| 修改 | `vite.config.js` (base 配置) | 轻微 |
