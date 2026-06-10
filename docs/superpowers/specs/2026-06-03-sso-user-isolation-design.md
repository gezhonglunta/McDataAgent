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
请求 → Spring Cloud Gateway (JWT验签/转发)
     → /nl2sql/ 前缀转发到本系统（可保留前缀，也可剥离前缀）
     → JwtAuthenticationWebFilter
        → 普通 API：解析 Authorization 中的 JWT，提取 userId
        → 响应成功后：回写签名的 user context cookie
        → 原生 SSE：浏览器自动携带 user context cookie，Filter 还原 userId
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
| `JwtAuthenticationWebFilter` | `filter/` | WebFilter，优先解析 `Authorization` 中的 JWT，并支持从签名的 user context cookie 恢复用户，将 JwtUser 同时写入 Reactor Context 和 ThreadLocal |
| `SpaWebFilter` | `filter/` | SPA History 模式 fallback，前端路由返回 index.html |

### JwtAuthenticationWebFilter 逻辑

```
请求进入 → 按 Authorization header → user context cookie → token cookie/query parameter 顺序解析用户
  → 若是前端入口、静态资源或前端 History 路由（如 /front/model-config）→ 直接放行
  → Authorization 存在 → 按配置进行 HS256 + base64Secret 验签（默认开启）
    → 解析 payload claims → 提取 jwt-claim-name 字段作为 userId → 构建 JwtUser
    → 向响应写入签名的 user context cookie（HttpOnly）
  → Authorization 不存在 → 尝试解析签名的 user context cookie → 成功则构建 JwtUser
  → 若仍无用户 → 再尝试兼容旧 token cookie / request parameter
  → 无可用身份 / 验签失败 / claim 缺失 → 返回 401
  → 将 JwtUser 同时写入 Reactor Context 和 ThreadLocal
  → 继续 filter chain
  → 请求结束时清理 ThreadLocal（防止线程池复用导致数据泄漏）
```

**SSE 回退策略**：前端分析流和会话推送改回原生 `EventSource`。由于浏览器原生 `EventSource` 不支持自定义 `Authorization` 请求头，普通 API 请求仍由前端从网关 cookie 中读取 token 并显式补充 `Authorization`；SSE 请求则依赖浏览器自动携带的 user context cookie，由 `JwtAuthenticationWebFilter` 在服务端恢复 `userId`。

**user context cookie 约束**：cookie 中不能直接存放裸 `userId` 作为可信身份来源，必须由服务端使用密钥签名后写入，例如 `base64url(userId) + "." + hmac`。Filter 仅在签名校验通过时才接受该 cookie，避免客户端伪造任意 `userId` 冒充其他用户。

**ThreadLocal 生命周期**：Filter 在请求开始时设置 ThreadLocal，在 `doFinally` 中清理，确保线程池场景下不会泄漏。

**前端路由放行规则**：`JwtAuthenticationWebFilter` 只应拦截真正需要用户身份的后端接口，不能拦截前端页面路由。对于 `/front/**` 下不带扩展名、且不是静态资源的路径，应视为前端 SPA History 路由并直接放行，例如：`/front/model-config`、`/front/agent/1/run`、`/front/user-agent/1/run`。这些请求后续由 `SpaWebFilter` 返回 `index.html`，再由前端路由接管。

**问题场景说明**：在线上网关保留 `/nl2sql` 前缀的部署中，浏览器访问 `http://<gateway>/nl2sql/front/model-config` 时，后端实际收到的路径是 `/nl2sql/front/model-config`。如果 `JwtAuthenticationWebFilter` 仅放行首页和静态资源，而不放行 `/front/**` 的 History 路由，请求会在到达 `SpaWebFilter` 之前被错误地返回 401，导致前端页面无法直接访问。

**解决方案**：将 `/front/**` 下的非静态、无扩展名前端路由加入 `JwtAuthenticationWebFilter` 的跳过鉴权规则，同时继续保持 `/api/**` 需要 JWT 鉴权、`/front/assets/**` 和 `/assets/**` 作为静态资源直接放行。这样可以保证“前端路由不鉴权，前端调用 API 时再鉴权”的边界清晰且稳定。

### 配置项

```yaml
spring.ai.alibaba.data-agent:
  auth:
    enabled: false          # 是否启用认证，默认关闭
    jwt-claim-name: sub     # JWT 中用户ID的 claim 名称
    jwt-secret: ""          # JWT HS256 签名密钥，base64 编码字符串
    signature-algorithm: HS256
    token-header-name: Authorization
    token-prefix: "Bearer "
    token-cookie-name: Bearer
    token-parameter-name: ""
    verify-signature: true
    user-context-cookie-name: data-agent-user
    user-context-cookie-secret: ""   # 默认可复用 jwt-secret
    user-context-cookie-same-site: Lax
  base-path: ""             # URL 前缀，空字符串表示无前缀
```

`base-path` 在应用启动时映射到 `spring.webflux.base-path`，由 Spring WebFlux 自动处理所有请求路径前缀。该配置表示**本系统实际收到的后端路径前缀**，不是浏览器侧网关路径前缀。

### 网关前缀转发模式

系统支持两种网关转发模式：

#### 模式一：网关保留 `/nl2sql` 前缀

```text
浏览器访问: /nl2sql/front/index.html
后端收到:   /nl2sql/front/index.html

浏览器访问: /nl2sql/front/assets/index.js
后端收到:   /nl2sql/front/assets/index.js

浏览器访问: /nl2sql/api/agent/list
后端收到:   /nl2sql/api/agent/list
```

此时配置：

```bash
DATA_AGENT_BASE_PATH=/nl2sql
VITE_FRONT_BASE=/nl2sql/front/
VITE_API_BASE=/nl2sql
```

约束：当后端静态资源通过 `/front/**` 暴露时，前端构建产物中的 `base` 必须包含完整浏览器访问前缀 `/nl2sql/front/`。不能仅配置为 `/nl2sql/`，否则 `index.html` 中生成的静态资源地址会变成 `/nl2sql/assets/**` 或 `/front/assets/**`，与实际资源映射不一致，经过网关访问首页时会出现 404。

#### 模式二：网关剥离 `/nl2sql` 前缀（推荐用于当前部署）

```text
浏览器访问: /nl2sql/agents
后端收到:   /agents

浏览器访问: /nl2sql/api/agent/list
后端收到:   /api/agent/list

浏览器访问: /nl2sql/assets/index.js
后端收到:   /assets/index.js
```

此时配置：

```bash
DATA_AGENT_BASE_PATH=
VITE_FRONT_BASE=/
VITE_API_BASE=
```

注意：如果网关已经剥离 `/nl2sql`，不要再配置 `DATA_AGENT_BASE_PATH=/nl2sql`，否则 Spring WebFlux 会期待后端收到的路径也带 `/nl2sql`，导致 `/api/**` 和前端路由无法匹配。

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
- `SessionEventController` 通过 `UserContextHolder.getCurrentUserId(exchange)` 获取当前用户，来源可以是 `Authorization` 解析结果，也可以是原生 SSE 自动携带的签名 user context cookie

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
VITE_FRONT_BASE=/nl2sql/front/
VITE_API_BASE=/nl2sql
```

说明：上述 `.env.production` 对应“网关保留 `/nl2sql` 前缀，且前端入口为 `/nl2sql/front/index.html`”的部署方式。如果生产环境改为“网关剥离 `/nl2sql` 前缀”，则应回退到 `VITE_FRONT_BASE=/`、`VITE_API_BASE=`。

**Vite 配置**：

```js
const FRONT_BASE = process.env.VITE_FRONT_BASE || '/'
export default {
  base: FRONT_BASE,
}
```

要求：生产构建前必须校验 `VITE_FRONT_BASE` 与静态资源实际暴露路径一致。对于当前 `/front/**` 资源映射，`VITE_FRONT_BASE` 必须以 `/front/` 结尾；在网关保留 `/nl2sql` 前缀时，必须为 `/nl2sql/front/`。

**Vue Router**：

```js
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})
```

### API 请求改造

前端调用普通 HTTP API 时，必须在发送请求前从浏览器 cookie 中读取当前网关对应的 token，并显式设置 `Authorization: Bearer <token>` 请求头。

**cookie 命名规则**：

- 优先读取 `Admin_<gateway-host>_<gateway-port>`
- 若不存在，再读取 `Bearer_<gateway-host>_<gateway-port>`
- 例如当前网关地址为 `190.160.2.132:8180` 时，优先读取 `Admin_190.160.2.132_8180`，否则退化到 `Bearer_190.160.2.132_8180`

说明：这里的 `<gateway-host>` 和 `<gateway-port>` 必须基于浏览器当前访问地址动态计算，不能写死固定值，也不能继续假设 cookie 名恒为 `Bearer`。

**公共请求封装**（`services/common.ts`）：

```ts
const currentGatewayPort = (): string => {
  if (window.location.port) {
    return window.location.port
  }
  return window.location.protocol === 'https:' ? '443' : '80'
}

const bearerToken = (): string | null => {
  const cookies = parseCookies()
  const port = currentGatewayPort()
  const adminCookieName = `Admin_${window.location.hostname}_${port}`
  const bearerCookieName = `Bearer_${window.location.hostname}_${port}`

  return cookies[adminCookieName]
    || cookies[bearerCookieName]
    || null
}

export const authHeaders = (): Record<string, string> => {
  const token = bearerToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

axios.interceptors.request.use(config => {
  const token = bearerToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

`apiFetch` 与 axios 请求拦截器都必须复用同一套 `bearerToken()` / `authHeaders()` 逻辑，避免部分接口漏传 `Authorization`。

边界说明：

- 仅普通 API 请求需要补充 `Authorization` 头
- 前端页面路由访问不需要携带该 header
- SSE 请求改回原生 `EventSource`，不再尝试在前端透传 `Authorization`
- SSE 所需用户身份由后端写入并校验签名 user context cookie，浏览器会自动随原生 `EventSource` 请求携带该 cookie

### 模型配置检查与 401 处理

前端全局路由守卫在进入业务页面前，会先调用 `/api/model-config/check-ready` 检查聊天模型和嵌入模型是否已完成配置。该逻辑用于区分“系统尚未完成模型初始化”和“页面本身可访问但运行能力不可用”两类状态。

前端需要提供统一的“未授权处理”公共方法，至少包含两部分能力：

- `isUnauthorizedError()`：统一识别 HTTP `401/403` 与业务 `401/403`
- `redirectToLoginWithPrompt()`：弹出“未登录，请先登录。”提示框，确认后通过 `window.location.href = '/admin/sso/admin/login'` 跳转到 SSO 登录页

模型配置相关接口不得各自散落实现未授权分支，`/api/model-config/check-ready` 与 `/api/model-config/list` 都应复用这套公共逻辑，保证用户在不同入口下看到一致的交互行为。

路由守卫行为约定：

- `/model-config` 页面本身直接放行，避免检查接口再次触发重定向
- `check-ready` 成功且 `ready=false` 时，提示用户补充模型配置，并跳转到 `/model-config`
- `check-ready` 返回 HTTP `401` 或 `403` 时，不再误判为“模型未配置”
- `check-ready` 返回业务 `401` 或业务 `403` 时，同样视为未授权。业务未授权的判定规则为：HTTP status 为 `200`，但响应体满足 `code == 401` 或 `code == 403`，其中 `code` 可为字符串或数值类型，只按 `code` 值判断
- `401/403` 场景下应先弹出提示框：`未登录，请先登录。`
- 用户点击确定后，通过 `window.location.href = '/admin/sso/admin/login'` 跳转到 SSO 登录页
- 这里必须使用浏览器整页跳转，而不是 `router.push()`，避免 `/admin/sso/admin/login` 被前端 SPA 路由错误接管
- 除 `401/403` 外的其他异常，仍按“模型状态检查失败”处理，提示后跳转到 `/model-config`

这样可以解决用户直接访问 `/user-agent/:id/run` 等前端路由时，因未登录或无权限导致 `/api/model-config/check-ready` 返回 HTTP `401/403` 或业务 `401/403`，却被错误重定向到 `/model-config` 的问题。认证失败与模型未配置在交互上必须分开处理：前者进入 SSO 登录流程，后者进入模型配置流程。

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

为兼容网关剥离 `/nl2sql` 前缀后的部署模式，`SpaWebFilter` 同时支持：

- 根路径前端路由：`/agents`、`/agent/1/run`、`/user-agent/1/run`
- 旧前端路径：`/front/agents`、`/front/model-config`、`/front/agent/1/run`
- 排除后端接口和静态资源：`/api/**`、`/uploads/**`、`/assets/**`、`/front/assets/**`、`/actuator/**`、`/v3/api-docs/**`、`/swagger-ui/**`、`/h2-console/**`、`/mcp/**`

注意：`SpaWebFilter` 生效的前提是前端路由请求必须先穿过 `JwtAuthenticationWebFilter`。因此两个 Filter 的路径边界必须一致：前端页面路由由 `JwtAuthenticationWebFilter` 放行，再由 `SpaWebFilter` fallback 到 `index.html`；只有真正的后端接口路径才由 JWT 过滤器强制鉴权。

## 部署场景矩阵

| 场景 | 网关是否剥离 `/nl2sql` | base-path | auth.enabled | 前端 base | API base | 行为 |
|------|--------------------------|-----------|-------------|-----------|----------|------|
| 开发环境 | 无网关 | `""` | `false` | `/` | `""` | 完全兼容原有行为 |
| 生产环境（网关剥离前缀） | 是 | `""` | `true` | `/` | `""` | `/nl2sql/agents` 转发为 `/agents`，SSO + 用户隔离 |
| 生产环境（网关保留前缀） | 否 | `/nl2sql` | `true` | `/nl2sql/front/` | `/nl2sql` | 首页从 `/nl2sql/front/index.html` 访问，静态资源与 API 都通过网关前缀访问 |
| 独立部署 | 无网关 | `""` | `true` | `/` | `""` | 有认证但无前缀 |

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
