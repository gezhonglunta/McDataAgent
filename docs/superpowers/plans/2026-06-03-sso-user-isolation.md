# SSO单点登录与会话用户隔离 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 DataAgent 系统新增 SSO 单点登录、会话用户隔离、URL 前缀配置和前后端一体化打包功能。

**Architecture:** 基于 WebFlux WebFilter + Reactor Context 实现 JWT 解析和用户信息传递，通过 ThreadLocal 兜底支持同步代码路径。所有功能通过配置开关控制，向后兼容。

**Tech Stack:** Spring Boot 3.4.8, WebFlux, MyBatis, Vue 3, Vite, Maven

---

## Task 1: 数据库 Schema 变更

**Files:**
- Modify: `data-agent-management/src/main/resources/sql/schema.sql:173-189`
- Modify: `data-agent-management/src/main/resources/sql/h2/schema-h2.sql` (对应位置)

- [ ] **Step 1: 修改 MySQL schema.sql**

将 `chat_session` 表的 `user_id` 字段类型从 `BIGINT` 改为 `VARCHAR(33)`：

```sql
CREATE TABLE IF NOT EXISTS chat_session (
  id VARCHAR(36) NOT NULL COMMENT '会话ID（UUID）',
  agent_id INT NOT NULL COMMENT '智能体ID',
  title VARCHAR(255) DEFAULT '新对话' COMMENT '会话标题',
  status VARCHAR(50) DEFAULT 'active' COMMENT '状态：active-活跃，archived-归档，deleted-已删除',
  is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
  user_id VARCHAR(33) COMMENT '用户ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  INDEX idx_agent_id (agent_id),
  INDEX idx_user_id (user_id),
  INDEX idx_status (status),
  INDEX idx_is_pinned (is_pinned),
  INDEX idx_create_time (create_time),
  FOREIGN KEY (agent_id) REFERENCES agent(id) ON DELETE CASCADE
) ENGINE = InnoDB COMMENT = '聊天会话表';
```

- [ ] **Step 2: 修改 H2 schema-h2.sql**

同步修改 H2 版本的建表语句，将 `user_id` 改为 `VARCHAR(33)`。

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/src/main/resources/sql/
git commit -m "feat: change chat_session.user_id from BIGINT to VARCHAR(33)"
```

---

## Task 2: ChatSession 实体类修改

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/entity/ChatSession.java`

- [ ] **Step 1: 修改 userId 字段类型**

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

	private String id;
	private Integer agentId;
	private String title;
	private String status;

	@Builder.Default
	private Boolean isPinned = false;

	private String userId; // 从 Long 改为 String
	private LocalDateTime createTime;
	private LocalDateTime updateTime;

	public ChatSession(String id, Integer agentId, String title, String status, String userId) {
		this.id = id;
		this.agentId = agentId;
		this.title = title;
		this.status = status;
		this.isPinned = false;
		this.userId = userId;
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/entity/ChatSession.java
git commit -m "feat: change ChatSession.userId from Long to String"
```

---

## Task 3: 新增 JwtUser DTO

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/dto/JwtUser.java`

- [ ] **Step 1: 创建 JwtUser 类**

```java
package com.alibaba.cloud.ai.dataagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtUser {
	private String userId;
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/dto/JwtUser.java
git commit -m "feat: add JwtUser DTO"
```

---

## Task 4: 新增 UserContextHolder 工具类

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/util/UserContextHolder.java`

- [ ] **Step 1: 创建 UserContextHolder 类**

```java
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.dto.JwtUser;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class UserContextHolder {

	private static final ThreadLocal<JwtUser> THREAD_LOCAL = new ThreadLocal<>();

	public static Context write(Context context, JwtUser user) {
		THREAD_LOCAL.set(user);
		return context.put(JwtUser.class, user);
	}

	public static Mono<JwtUser> read() {
		return Mono.deferContextual(ctx -> {
			if (ctx.hasKey(JwtUser.class)) {
				return Mono.just(ctx.get(JwtUser.class));
			}
			return Mono.empty();
		});
	}

	public static String getCurrentUserId() {
		JwtUser user = THREAD_LOCAL.get();
		return user != null ? user.getUserId() : null;
	}

	public static void clear() {
		THREAD_LOCAL.remove();
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/util/UserContextHolder.java
git commit -m "feat: add UserContextHolder with Reactor Context and ThreadLocal"
```

---

## Task 5: 新增 JwtAuthenticationWebFilter

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/JwtAuthenticationWebFilter.java`

- [ ] **Step 1: 创建 Filter 类**

```java
package com.alibaba.cloud.ai.dataagent.filter;

import com.alibaba.cloud.ai.dataagent.dto.JwtUser;
import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.auth.enabled", havingValue = "true")
public class JwtAuthenticationWebFilter implements WebFilter {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${spring.ai.alibaba.data-agent.auth.jwt-claim-name:sub}")
	private String jwtClaimName;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String token = extractToken(exchange);

		if (token == null) {
			return chain.filter(exchange);
		}

		JwtUser user = parseJwt(token);

		if (user != null) {
			return chain.filter(exchange)
				.contextWrite(ctx -> UserContextHolder.write(ctx, user))
				.doFinally(signal -> UserContextHolder.clear());
		}

		return chain.filter(exchange);
	}

	private String extractToken(ServerWebExchange exchange) {
		String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			return authHeader.substring(7);
		}
		if (exchange.getRequest().getCookies().containsKey("Bearer")) {
			return exchange.getRequest().getCookies().getFirst("Bearer").getValue();
		}
		return null;
	}

	private JwtUser parseJwt(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				log.warn("Invalid JWT format");
				return null;
			}

			String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			JsonNode jsonNode = objectMapper.readTree(payload);
			String userId = jsonNode.path(jwtClaimName).asText(null);

			if (userId != null && !userId.isEmpty()) {
				return new JwtUser(userId);
			}
		} catch (Exception e) {
			log.warn("Failed to parse JWT: {}", e.getMessage());
		}
		return null;
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/JwtAuthenticationWebFilter.java
git commit -m "feat: add JwtAuthenticationWebFilter for JWT parsing"
```

---

## Task 6: 修改 ChatSessionMapper

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/mapper/ChatSessionMapper.java`

- [ ] **Step 1: 修改 selectByAgentId 方法**

```java
@Select("""
		<script>
		SELECT * FROM chat_session
		WHERE agent_id = #{agentId} AND status != 'deleted'
		<if test="userId != null"> AND user_id = #{userId} </if>
		ORDER BY is_pinned DESC, update_time DESC
		</script>
		""")
List<ChatSession> selectByAgentId(@Param("agentId") Integer agentId, @Param("userId") String userId);
```

- [ ] **Step 2: 修改 softDeleteByAgentId 方法**

```java
@Update("""
		<script>
		UPDATE chat_session
		SET status = 'deleted', update_time = #{updateTime}
		WHERE agent_id = #{agentId}
		<if test="userId != null"> AND user_id = #{userId} </if>
		</script>
		""")
int softDeleteByAgentId(@Param("agentId") Integer agentId, @Param("userId") String userId,
		@Param("updateTime") LocalDateTime updateTime);
```

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/mapper/ChatSessionMapper.java
git commit -m "feat: add userId filter to ChatSessionMapper queries"
```

---

## Task 7: 修改 ChatSessionService 接口

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatSessionService.java`

- [ ] **Step 1: 更新接口方法签名**

```java
public interface ChatSessionService {
	List<ChatSession> findByAgentId(Integer agentId, String userId);
	ChatSession createSession(Integer agentId, String title, String userId);
	ChatSession findBySessionId(String sessionId);
	void clearSessionsByAgentId(Integer agentId, String userId);
	void updateSessionTime(String sessionId);
	void pinSession(String sessionId, boolean isPinned);
	void renameSession(String sessionId, String newTitle);
	void deleteSession(String sessionId);

	default List<ChatSession> findByAgentId(Integer agentId) {
		return findByAgentId(agentId, null);
	}

	default void clearSessionsByAgentId(Integer agentId) {
		clearSessionsByAgentId(agentId, null);
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatSessionService.java
git commit -m "feat: add userId parameter to ChatSessionService methods"
```

---

## Task 8: 修改 ChatSessionServiceImpl

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatSessionServiceImpl.java`

- [ ] **Step 1: 更新实现方法**

```java
@Override
public List<ChatSession> findByAgentId(Integer agentId, String userId) {
	return chatSessionMapper.selectByAgentId(agentId, userId);
}

@Override
public ChatSession createSession(Integer agentId, String title, String userId) {
	String sessionId = UUID.randomUUID().toString();
	ChatSession session = new ChatSession(sessionId, agentId, title != null ? title : "新会话", "active", userId);
	chatSessionMapper.insert(session);
	log.info("Created new chat session: {} for agent: {}, user: {}", sessionId, agentId, userId);
	return session;
}

@Override
public void clearSessionsByAgentId(Integer agentId, String userId) {
	LocalDateTime now = LocalDateTime.now();
	int updated = chatSessionMapper.softDeleteByAgentId(agentId, userId, now);
	log.info("Cleared {} sessions for agent: {}, user: {}", updated, agentId, userId);
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/ChatSessionServiceImpl.java
git commit -m "feat: implement userId filtering in ChatSessionServiceImpl"
```

---

## Task 9: 修改 ChatController

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/ChatController.java`

- [ ] **Step 1: 修改 getAgentSessions 方法**

```java
@GetMapping("/agent/{id}/sessions")
public ResponseEntity<List<ChatSession>> getAgentSessions(@PathVariable(value = "id") Integer id) {
	String userId = UserContextHolder.getCurrentUserId();
	List<ChatSession> sessions = chatSessionService.findByAgentId(id, userId);
	return ResponseEntity.ok(sessions);
}
```

- [ ] **Step 2: 修改 createSession 方法**

```java
@PostMapping("/agent/{id}/sessions")
public ResponseEntity<ChatSession> createSession(@PathVariable(value = "id") Integer id,
		@RequestBody(required = false) Map<String, Object> request) {
	String title = request != null ? (String) request.get("title") : null;
	String userId = UserContextHolder.getCurrentUserId();
	ChatSession session = chatSessionService.createSession(id, title, userId);
	return ResponseEntity.ok(session);
}
```

- [ ] **Step 3: 修改 clearAgentSessions 方法**

```java
@DeleteMapping("/agent/{id}/sessions")
public ResponseEntity<ApiResponse> clearAgentSessions(@PathVariable(value = "id") Integer id) {
	String userId = UserContextHolder.getCurrentUserId();
	chatSessionService.clearSessionsByAgentId(id, userId);
	return ResponseEntity.ok(ApiResponse.success("会话已清空"));
}
```

- [ ] **Step 4: 添加 import**

```java
import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
```

- [ ] **Step 5: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/ChatController.java
git commit -m "feat: integrate UserContextHolder in ChatController"
```

---

## Task 10: 修改 SessionEventPublisher

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/SessionEventPublisher.java`

- [ ] **Step 1: 修改 Sink key 结构**

将 key 从 `Integer agentId` 改为 `String agentId + ":" + userId`：

```java
private final Map<String, AgentSessionSink> sinks = new ConcurrentHashMap<>();

public Flux<ServerSentEvent<SessionUpdateEvent>> register(Integer agentId, String userId) {
	String key = agentId + ":" + (userId != null ? userId : "");
	AgentSessionSink sink = sinks.computeIfAbsent(key, k -> new AgentSessionSink());
	Flux<ServerSentEvent<SessionUpdateEvent>> heartbeat = Flux.interval(Duration.ofSeconds(2))
		.map(i -> ServerSentEvent.<SessionUpdateEvent>builder().comment("heartbeat").build());
	sink.increment();
	log.debug("Registered subscriber for key {}, current count: {}", key, sink.subscribers.get());
	return Flux.merge(heartbeat, sink.sink.asFlux()).doFinally(signalType -> cleanup(key, sink, signalType));
}

public void publishTitleUpdated(Integer agentId, String userId, String sessionId, String title) {
	if (agentId == null) {
		return;
	}
	String key = agentId + ":" + (userId != null ? userId : "");
	SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
	AgentSessionSink sink = sinks.get(key);
	if (sink == null) {
		log.debug("No active subscribers for key {}, skip pushing session title update", key);
		return;
	}
	Sinks.EmitResult result = sink.sink.tryEmitNext(ServerSentEvent.builder(event).event(event.getType()).build());
	if (result.isFailure()) {
		log.warn("Failed to emit session title update for key {}, session {}, reason {}", key, sessionId, result);
	}
}

private void cleanup(String key, AgentSessionSink sink, SignalType signalType) {
	int current = sink.decrement();
	log.debug("Cleanup called for key {}, signal: {}, remaining subscribers: {}", key, signalType, current);
	if (current <= 0) {
		if (sinks.remove(key, sink)) {
			sink.sink.tryEmitComplete();
			log.debug("Removed session update sink for key {}", key);
		}
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/chat/SessionEventPublisher.java
git commit -m "feat: add userId to SessionEventPublisher sink key"
```

---

## Task 11: 修改 SessionEventController

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/SessionEventController.java`

- [ ] **Step 1: 修改 streamSessionUpdates 方法**

```java
@GetMapping(value = "/agent/{agentId}/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<SessionUpdateEvent>> streamSessionUpdates(@PathVariable Integer agentId,
		ServerHttpResponse response) {
	response.getHeaders().add("Cache-Control", "no-cache");
	response.getHeaders().add("Connection", "keep-alive");
	response.getHeaders().add("Access-Control-Allow-Origin", "*");

	String userId = UserContextHolder.getCurrentUserId();
	log.debug("Client subscribed to session update stream for agent {}, user {}", agentId, userId);
	return sessionEventPublisher.register(agentId, userId)
		.doFinally(signal -> log.debug("Session update stream finished for agent {} with signal {}", agentId, signal));
}
```

- [ ] **Step 2: 添加 import**

```java
import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
```

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/SessionEventController.java
git commit -m "feat: integrate UserContextHolder in SessionEventController"
```

---

## Task 12: 添加配置项

**Files:**
- Modify: `data-agent-management/src/main/resources/application.yml`
- Modify: `data-agent-management/src/main/resources/application-h2.yml`

- [ ] **Step 1: 在 application.yml 中添加配置**

在 `spring.ai.alibaba.data-agent` 下添加：

```yaml
auth:
  enabled: ${DATA_AGENT_AUTH_ENABLED:false}
  jwt-claim-name: ${DATA_AGENT_JWT_CLAIM_NAME:sub}
base-path: ${DATA_AGENT_BASE_PATH:}
```

- [ ] **Step 2: 在 application-h2.yml 中添加配置**

同样添加上述配置项。

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/src/main/resources/application*.yml
git commit -m "feat: add auth and base-path configuration"
```

---

## Task 13: 修改 WebConfig 支持 base-path

**Files:**
- Modify: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/config/WebConfig.java`

- [ ] **Step 1: 添加 base-path 支持**

```java
@Configuration
@AllArgsConstructor
public class WebConfig implements WebFluxConfigurer {

	private final FileStorageProperties fileStorageProperties;

	@Value("${spring.ai.alibaba.data-agent.base-path:}")
	private String basePath;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String uploadDir = Paths.get(fileStorageProperties.getPath()).toAbsolutePath().toString();
		String uploadPrefix = basePath + fileStorageProperties.getUrlPrefix();
		registry.addResourceHandler(uploadPrefix + "/**")
			.addResourceLocations("file:" + uploadDir + "/")
			.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

		registry.addResourceHandler(basePath + "/front/**")
			.addResourceLocations("classpath:/static/front/");
	}
}
```

- [ ] **Step 2: 添加 import**

```java
import org.springframework.beans.factory.annotation.Value;
```

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/config/WebConfig.java
git commit -m "feat: add base-path support and frontend static resources"
```

---

## Task 14: 新增 SpaWebFilter

**Files:**
- Create: `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/SpaWebFilter.java`

- [ ] **Step 1: 创建 SPA fallback filter**

```java
package com.alibaba.cloud.ai.dataagent.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaWebFilter implements WebFilter {

	@Value("${spring.ai.alibaba.data-agent.base-path:}")
	private String basePath;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		String frontPrefix = basePath + "/front";

		if (!path.startsWith(frontPrefix)) {
			return chain.filter(exchange);
		}

		String subPath = path.substring(frontPrefix.length());
		if (subPath.startsWith("/api") || subPath.contains(".")) {
			return chain.filter(exchange);
		}

		return Mono.fromCallable(() -> {
			ClassPathResource resource = new ClassPathResource("static/front/index.html");
			byte[] bytes = resource.getInputStream().readAllBytes();
			exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
			exchange.getResponse().getHeaders().setContentLength(bytes.length);
			return exchange.getResponse().writeWith(
				Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
			);
		}).flatMap(mono -> mono);
	}
}
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/filter/SpaWebFilter.java
git commit -m "feat: add SpaWebFilter for frontend routing fallback"
```

---

## Task 15: 前端环境变量配置

**Files:**
- Create: `data-agent-frontend/.env.development`
- Create: `data-agent-frontend/.env.production`

- [ ] **Step 1: 创建 .env.development**

```bash
VITE_FRONT_BASE=/
VITE_API_BASE=
```

- [ ] **Step 2: 创建 .env.production**

```bash
VITE_FRONT_BASE=/front/
VITE_API_BASE=/nl2sql
```

- [ ] **Step 3: 提交**

```bash
git add data-agent-frontend/.env.*
git commit -m "feat: add frontend environment variables"
```

---

## Task 16: 修改 Vite 配置

**Files:**
- Modify: `data-agent-frontend/vite.config.js`

- [ ] **Step 1: 添加 base 配置**

```js
export default defineConfig({
  base: process.env.VITE_FRONT_BASE || '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8065',
        changeOrigin: true,
      },
      '/nl2sql': {
        target: 'http://localhost:8065',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8065',
        changeOrigin: true,
      },
    },
    historyApiFallback: true,
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
  },
});
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-frontend/vite.config.js
git commit -m "feat: add base path configuration to vite"
```

---

## Task 17: 修改 Vue Router

**Files:**
- Modify: `data-agent-frontend/src/router/index.js`

- [ ] **Step 1: 使用 BASE_URL**

```js
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition;
    else return { top: 0 };
  },
});
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-frontend/src/router/index.js
git commit -m "feat: use BASE_URL for router history"
```

---

## Task 18: 添加 Axios 拦截器

**Files:**
- Modify: `data-agent-frontend/src/services/common.ts`

- [ ] **Step 1: 添加拦截器**

在文件末尾添加：

```ts
import axios from 'axios';

axios.interceptors.request.use(config => {
  const match = document.cookie.match(/Bearer=([^;]+)/);
  if (match) {
    config.headers.Authorization = `Bearer ${match[1]}`;
  }
  return config;
});
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-frontend/src/services/common.ts
git commit -m "feat: add Authorization header interceptor"
```

---

## Task 19: 新增用户级路由

**Files:**
- Modify: `data-agent-frontend/src/router/routes.js`

- [ ] **Step 1: 添加新路由**

在 `AgentRun` 路由后面添加：

```js
{
  path: '/user-agent/:id/run',
  name: 'UserAgentRun',
  component: () => import('@/views/AgentRun.vue'),
  meta: { title: '运行智能体', module: 'agent', hideHeader: true },
},
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-frontend/src/router/routes.js
git commit -m "feat: add user-agent route with hideHeader"
```

---

## Task 20: 修改 BaseLayout

**Files:**
- Modify: `data-agent-frontend/src/layouts/BaseLayout.vue`

- [ ] **Step 1: 添加条件隐藏 header**

```vue
<template>
  <div class="base-layout">
    <header v-if="!hideHeader" class="page-header">
      <div class="header-content">
        <div class="brand-section">
          <div class="brand-logo">
            <i class="bi bi-robot"></i>
            <span class="brand-text">Spring AI Alibaba Data Agent</span>
          </div>
          <nav class="header-nav">
            <div class="nav-item" :class="{ active: isAgentPage() }" @click="goToAgentList">
              <i class="bi bi-grid-3x3-gap"></i>
              <span>智能体列表</span>
            </div>
            <div class="nav-item" :class="{ active: isModelConfigPage() }" @click="goToModelConfig">
              <i class="bi bi-gear"></i>
              <span>模型配置</span>
            </div>
          </nav>
        </div>
      </div>
    </header>
    <main class="page-content" :class="{ 'full-height': hideHeader }">
      <slot></slot>
    </main>
  </div>
</template>

<script>
  import { useRouter } from 'vue-router';
  import { computed } from 'vue';

  export default {
    name: 'BaseLayout',
    setup() {
      const router = useRouter();
      const hideHeader = computed(() => router.currentRoute.value.meta?.hideHeader === true);
      const goToAgentList = () => { router.push('/agents'); };
      const goToModelConfig = () => { router.push('/model-config'); };
      const isAgentPage = () => {
        return (
          router.currentRoute.value.name === 'AgentList' ||
          router.currentRoute.value.name === 'AgentDetail' ||
          router.currentRoute.value.name === 'AgentCreate' ||
          router.currentRoute.value.name === 'AgentRun' ||
          router.currentRoute.value.name === 'UserAgentRun'
        );
      };
      const isModelConfigPage = () => {
        return router.currentRoute.value.name === 'ModelConfig';
      };
      return { hideHeader, goToAgentList, goToModelConfig, isAgentPage, isModelConfigPage };
    },
  };
</script>

<style scoped>
  .base-layout { min-height: 100vh; background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%); display: flex; flex-direction: column; }
  .page-header { background: white; border-bottom: 1px solid #e2e8f0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 100; }
  .header-content { width: 100%; padding: 0 1.5rem; display: flex; align-items: center; justify-content: space-between; height: 4rem; }
  .brand-section { display: flex; align-items: center; gap: 2rem; }
  .brand-logo { display: flex; align-items: center; gap: 0.75rem; font-size: 1.25rem; font-weight: 600; color: #1e293b; }
  .brand-logo i { font-size: 1.5rem; color: #3b82f6; }
  .header-nav { display: flex; align-items: center; gap: 0.5rem; }
  .nav-item { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 1rem; border-radius: 8px; cursor: pointer; transition: all 0.2s ease; color: #64748b; font-weight: 500; }
  .nav-item:hover { background: #f1f5f9; color: #334155; }
  .nav-item.active { background: #e0f2fe; color: #0369a1; }
  .nav-item i { font-size: 1rem; }
  .page-content { flex: 1; padding: 0; }
  .page-content.full-height { height: 100vh; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add data-agent-frontend/src/layouts/BaseLayout.vue
git commit -m "feat: add hideHeader support based on route meta"
```

---

## Task 21: Maven 前后端一体化打包

**Files:**
- Modify: `data-agent-management/pom.xml`

- [ ] **Step 1: 添加 frontend-maven-plugin**

在 `<build><plugins>` 中添加：

```xml
<plugin>
  <groupId>com.github.eirslett</groupId>
  <artifactId>frontend-maven-plugin</artifactId>
  <version>1.15.1</version>
  <configuration>
    <workingDirectory>../data-agent-frontend</workingDirectory>
  </configuration>
  <executions>
    <execution>
      <id>install-node-and-npm</id>
      <goals>
        <goal>install-node-and-npm</goal>
      </goals>
      <configuration>
        <nodeVersion>v20.18.0</nodeVersion>
      </configuration>
    </execution>
    <execution>
      <id>npm-install</id>
      <goals>
        <goal>npm</goal>
      </goals>
    </execution>
    <execution>
      <id>npm-build</id>
      <goals>
        <goal>npm</goal>
      </goals>
      <configuration>
        <arguments>run build</arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

- [ ] **Step 2: 添加 maven-resources-plugin**

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-resources-plugin</artifactId>
  <executions>
    <execution>
      <id>copy-frontend</id>
      <phase>generate-resources</phase>
      <goals>
        <goal>copy-resources</goal>
      </goals>
      <configuration>
        <outputDirectory>${project.build.outputDirectory}/static/front</outputDirectory>
        <resources>
          <resource>
            <directory>../data-agent-frontend/dist</directory>
          </resource>
        </resources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

- [ ] **Step 3: 提交**

```bash
git add data-agent-management/pom.xml
git commit -m "feat: add frontend build integration to Maven"
```

---

## Task 22: 测试验证

- [ ] **Step 1: 后端编译测试**

```bash
cd data-agent-management
mvn clean compile -DskipTests
```

- [ ] **Step 2: 前端构建测试**

```bash
cd data-agent-frontend
npm install
npm run build
```

- [ ] **Step 3: 完整打包测试**

```bash
cd data-agent-management
mvn clean package -DskipTests
```

- [ ] **Step 4: 启动测试（H2 模式）**

```bash
cd data-agent-management
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

访问 `http://localhost:8065/front/` 验证前端加载。

- [ ] **Step 5: 提交所有修改**

```bash
git add .
git commit -m "feat: complete SSO and user isolation implementation"
```

---

## 实施完成检查清单

- [ ] 数据库 schema 已更新（MySQL + H2）
- [ ] ChatSession 实体 userId 已改为 String
- [ ] JwtUser、UserContextHolder、JwtAuthenticationWebFilter 已创建
- [ ] ChatSessionMapper、Service、Controller 已集成 userId
- [ ] SessionEventPublisher、Controller 已支持 userId 隔离
- [ ] 配置项已添加（auth.enabled、base-path）
- [ ] WebConfig 已支持 base-path 和前端静态资源
- [ ] SpaWebFilter 已创建
- [ ] 前端环境变量已配置
- [ ] Vite、Router、BaseLayout 已修改
- [ ] Axios 拦截器已添加
- [ ] 用户级路由已添加
- [ ] Maven 打包配置已完成
- [ ] 编译、构建、启动测试通过
