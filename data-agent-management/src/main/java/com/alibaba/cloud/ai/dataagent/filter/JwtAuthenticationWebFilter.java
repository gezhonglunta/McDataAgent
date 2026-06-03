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

import com.alibaba.cloud.ai.dataagent.dto.JwtUser;
import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
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
			return unauthorized(exchange);
		}

		JwtUser user = parseJwt(token);

		if (user != null) {
			return chain.filter(exchange)
				.contextWrite(ctx -> UserContextHolder.write(ctx, user))
				.doFinally(signal -> UserContextHolder.clear());
		}

		return unauthorized(exchange);
	}

	private Mono<Void> unauthorized(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		return exchange.getResponse().setComplete();
	}

	private String extractToken(ServerWebExchange exchange) {
		String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			return authHeader.substring(7);
		}
		HttpCookie cookie = exchange.getRequest().getCookies().getFirst("Bearer");
		if (cookie != null) {
			return cookie.getValue();
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
		}
		catch (Exception e) {
			log.warn("Failed to parse JWT: {}", e.getMessage());
		}
		return null;
	}

}
