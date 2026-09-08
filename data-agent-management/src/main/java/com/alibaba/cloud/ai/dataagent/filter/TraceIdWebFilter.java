/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.filter;

import com.alibaba.cloud.ai.dataagent.util.TraceIdMdcUtil;
import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求级日志追踪过滤器。在 {@link JwtAuthenticationWebFilter}（HIGHEST_PRECEDENCE）之后执行，
 * 读取其写入的用户上下文，将 userId 作为 traceId 放入 MDC；无用户上下文时使用随机 UUID。
 * 跨线程传播由 {@code TraceIdThreadLocalAccessor} 与线程池装饰器完成。
 *
 * @author vlsmb
 * @since 2026/9/8
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceIdWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String traceId = TraceIdMdcUtil.resolveTraceId(UserContextHolder.getCurrentUserId(exchange));
		return Mono.fromRunnable(() -> TraceIdMdcUtil.put(traceId))
			.then(chain.filter(exchange))
			.doFinally(signal -> TraceIdMdcUtil.clear());
	}

}
