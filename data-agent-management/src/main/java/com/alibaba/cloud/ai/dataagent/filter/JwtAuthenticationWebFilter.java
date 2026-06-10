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
import org.apache.commons.lang3.StringUtils;
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.auth.enabled", havingValue = "true")
public class JwtAuthenticationWebFilter implements WebFilter {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${spring.ai.alibaba.data-agent.auth.jwt-claim-name:sub}")
	String jwtClaimName = "sub";

	@Value("${spring.ai.alibaba.data-agent.auth.jwt-secret:}")
	String jwtSecret = "";

	@Value("${spring.ai.alibaba.data-agent.auth.signature-algorithm:HS256}")
	String signatureAlgorithm = "HS256";

	@Value("${spring.ai.alibaba.data-agent.auth.token-header-name:Authorization}")
	String tokenHeaderName = "Authorization";

	@Value("${spring.ai.alibaba.data-agent.auth.token-prefix:Bearer }")
	String tokenPrefix = "Bearer ";

	@Value("${spring.ai.alibaba.data-agent.auth.token-cookie-name:Bearer}")
	String tokenCookieName = "Bearer";

	@Value("${spring.ai.alibaba.data-agent.auth.token-parameter-name:}")
	String tokenParameterName = "";

	@Value("${spring.ai.alibaba.data-agent.auth.verify-signature:true}")
	boolean verifySignature = true;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();

		if (shouldSkipAuth(path)) {
			return chain.filter(exchange);
		}

		String token = extractToken(exchange);

		if (token == null) {
			log.warn("JWT authentication failed: token missing, path={}", path);
			return unauthorized(exchange);
		}
		log.debug("JWT token extracted: path={}, {}", path, tokenFingerprint(token));

		JwtUser user = parseJwt(token);

		if (user != null) {
			log.debug("JWT authentication succeeded: path={}, claim={}, userId={}", path, jwtClaimName,
					maskUserId(user.getUserId()));
			UserContextHolder.write(exchange, user);
			return chain.filter(exchange)
					.contextWrite(ctx -> UserContextHolder.write(ctx, user))
					.doFinally(signal -> UserContextHolder.clear());
		}

		log.warn("JWT authentication failed: token parse returned empty user, path={}, claim={}, {}", path,
				jwtClaimName, tokenFingerprint(token));
		return unauthorized(exchange);
	}

	private Mono<Void> unauthorized(ServerWebExchange exchange) {
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		return exchange.getResponse().setComplete();
	}

	static boolean shouldSkipAuth(String path) {
		if (path == null) {
			return false;
		}
		if (StringUtils.contains(path, "/api/test/")) {
			return false;
		}
		if ("/".equals(path) || "/index.html".equals(path)) {
			return true;
		}
		if (path.startsWith("/front/assets") || path.startsWith("/assets") || path.startsWith("/front/vendor")
				|| path.startsWith("/vendor")) {
			return true;
		}
		if (path.startsWith("/front/") && !path.startsWith("/front/api/") && !path.startsWith("/front/assets")) {
			return !path.contains(".");
		}
		if (path.contains(".")) {
			return true;
		}
		return false;
	}

	String extractToken(ServerWebExchange exchange) {
		String authHeader = exchange.getRequest().getHeaders().getFirst(tokenHeaderName);
		if (org.springframework.util.StringUtils.hasText(authHeader) && authHeader.startsWith(tokenPrefix)) {
			log.debug("JWT token source: Authorization header");
			return authHeader.substring(tokenPrefix.length());
		}
		HttpCookie cookie = exchange.getRequest().getCookies().getFirst(tokenCookieName);
		if (cookie != null) {
			log.debug("JWT token source: Bearer cookie");
			return cookie.getValue();
		}
		if (org.springframework.util.StringUtils.hasText(tokenParameterName)) {
			String parameterToken = exchange.getRequest().getQueryParams().getFirst(tokenParameterName);
			if (org.springframework.util.StringUtils.hasText(parameterToken)) {
				log.debug("JWT token source: request parameter {}", tokenParameterName);
				return parameterToken;
			}
		}
		return null;
	}

	JwtUser parseJwt(String token) {
		try {
			String[] parts = token.split("\\.");
			log.debug("JWT parse step: parts={}, {}", parts.length, tokenFingerprint(token));
			if (parts.length != 3) {
				log.warn("Invalid JWT format: parts={}, {}", parts.length, tokenFingerprint(token));
				return null;
			}
			if (verifySignature) {
				verifySignature(parts, token);
			}

			String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			log.debug("JWT parse step: payload decoded, bytes={}, claim={}",
					payload.getBytes(StandardCharsets.UTF_8).length, jwtClaimName);
			JsonNode jsonNode = objectMapper.readTree(payload);
			String userId = jsonNode.path(jwtClaimName).asText(null);

			if (userId != null && !userId.isEmpty()) {
				log.debug("JWT parse step: claim resolved, claim={}, userId={}", jwtClaimName, maskUserId(userId));
				return new JwtUser(userId);
			}
			log.warn("JWT parse step: claim missing or empty, claim={}, {}", jwtClaimName, tokenFingerprint(token));
		} catch (Exception e) {
			log.warn("Failed to parse JWT: {}, {}", e.getMessage(), tokenFingerprint(token));
		}
		return null;
	}

	private void verifySignature(String[] parts, String token) throws SignatureException {
		if (!"HS256".equalsIgnoreCase(signatureAlgorithm)) {
			throw new SignatureException("Unsupported JWT signature algorithm: " + signatureAlgorithm);
		}
		if (!org.springframework.util.StringUtils.hasText(jwtSecret)) {
			throw new SignatureException("JWT secret is empty");
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(jwtSecret), "HmacSHA256"));
			String content = parts[0] + "." + parts[1];
			String expected = Base64.getUrlEncoder()
					.withoutPadding()
					.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
			if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
					parts[2].getBytes(StandardCharsets.UTF_8))) {
				throw new SignatureException("JWT signature validation failed");
			}
			log.debug("JWT parse step: signature verified, algorithm={}, {}", signatureAlgorithm,
					tokenFingerprint(token));
		} catch (IllegalArgumentException ex) {
			throw new SignatureException("JWT secret is not valid base64", ex);
		} catch (SignatureException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SignatureException("JWT signature validation failed: " + ex.getMessage(), ex);
		}
	}

	static String tokenFingerprint(String token) {
		if (token == null) {
			return "token=null";
		}
		return "token(len=" + token.length() + ", sha256=" + sha256Hex(token, StandardCharsets.UTF_8).substring(0, 12)
				+ ")";
	}

	static String maskUserId(String userId) {
		if (userId == null || userId.length() <= 6) {
			return "***";
		}
		return userId.substring(0, 3) + "***" + userId.substring(userId.length() - 3);
	}

	private static String sha256Hex(String value, Charset charset) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(charset));
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
