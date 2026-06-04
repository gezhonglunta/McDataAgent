/*
 * Copyright 2026 the original author or authors.
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SpaWebFilter implements WebFilter {

	@Value("${spring.ai.alibaba.data-agent.base-path:}")
	private String basePath;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();

		if (!shouldFallbackToIndex(path, basePath)) {
			return chain.filter(exchange);
		}

		ClassPathResource resource = new ClassPathResource("static/front/index.html");
		return Mono.fromCallable(() -> resource.getInputStream().readAllBytes()).flatMap(bytes -> {
			exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
			exchange.getResponse().getHeaders().setContentLength(bytes.length);
			return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
		});
	}

	static boolean shouldFallbackToIndex(String path, String basePath) {
		String normalizedPath = stripBasePath(path, basePath);
		if (!StringUtils.hasText(normalizedPath) || "/".equals(normalizedPath)) {
			return true;
		}
		if (normalizedPath.contains(".")) {
			return false;
		}
		if (normalizedPath.startsWith("/api") || normalizedPath.startsWith("/uploads")
				|| normalizedPath.startsWith("/actuator") || normalizedPath.startsWith("/v3/api-docs")
				|| normalizedPath.startsWith("/swagger-ui") || normalizedPath.startsWith("/h2-console")
				|| normalizedPath.startsWith("/mcp")) {
			return false;
		}
		if (normalizedPath.startsWith("/front/assets") || normalizedPath.startsWith("/assets")) {
			return false;
		}
		return true;
	}

	private static String stripBasePath(String path, String basePath) {
		if (!StringUtils.hasText(basePath) || "/".equals(basePath)) {
			return path;
		}
		String normalizedBasePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
		if (path.startsWith(normalizedBasePath)) {
			String stripped = path.substring(normalizedBasePath.length());
			return StringUtils.hasText(stripped) ? stripped : "/";
		}
		return path;
	}

}
