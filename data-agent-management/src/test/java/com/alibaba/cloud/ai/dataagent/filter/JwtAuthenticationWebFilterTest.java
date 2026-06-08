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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationWebFilterTest {

	@Test
	void tokenFingerprintDoesNotExposeRawToken() {
		String token = "header.payload.signature";

		String fingerprint = JwtAuthenticationWebFilter.tokenFingerprint(token);

		assertThat(fingerprint).contains("len=24").contains("sha256=").doesNotContain(token);
	}

	@Test
	void maskUserIdKeepsOnlyEdges() {
		assertThat(JwtAuthenticationWebFilter.maskUserId("user-1234567890")).isEqualTo("use***890");
	}

	@Test
	void maskUserIdHandlesShortValue() {
		assertThat(JwtAuthenticationWebFilter.maskUserId("abc")).isEqualTo("***");
	}

	@Test
	void parseJwtVerifiesHs256SignatureWithBase64Secret() {
		String secret = Base64.getEncoder()
			.encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
		String token = signedHs256Token("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", "{\"sub\":\"user-001\"}", secret);
		JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter();
		filter.jwtSecret = secret;

		assertThat(filter.parseJwt(token).getUserId()).isEqualTo("user-001");
	}

	@Test
	void parseJwtRejectsInvalidSignatureWhenVerificationEnabled() {
		String secret = Base64.getEncoder()
			.encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
		String wrongSecret = Base64.getEncoder()
			.encodeToString("abcdefghijabcdefghijabcdefghij12".getBytes(StandardCharsets.UTF_8));
		String token = signedHs256Token("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", "{\"sub\":\"user-001\"}", wrongSecret);
		JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter();
		filter.jwtSecret = secret;

		assertThat(filter.parseJwt(token)).isNull();
	}

	@Test
	void extractTokenSupportsConfiguredHeaderCookieAndParameter() {
		JwtAuthenticationWebFilter filter = new JwtAuthenticationWebFilter();
		filter.tokenHeaderName = "X-Token";
		filter.tokenPrefix = "Token ";
		filter.tokenCookieName = "JwtCookie";
		filter.tokenParameterName = "access_token";

		MockServerWebExchange headerExchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/test").header("X-Token", "Token header-token"));
		MockServerWebExchange cookieExchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/test").cookie(new HttpCookie("JwtCookie", "cookie-token")));
		MockServerWebExchange parameterExchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("/api/test?access_token=parameter-token"));

		assertThat(filter.extractToken(headerExchange)).isEqualTo("header-token");
		assertThat(filter.extractToken(cookieExchange)).isEqualTo("cookie-token");
		assertThat(filter.extractToken(parameterExchange)).isEqualTo("parameter-token");
	}

	@Test
	void shouldSkipAuthForStaticResources() {
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/index.html")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/assets/index.js")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/front/assets/index.css")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/vendor/highlightjs/11.9.0/atom-one-dark.min.css"))
			.isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/front/vendor/highlightjs/11.9.0/atom-one-dark.min.css"))
			.isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/favicon.ico")).isTrue();
	}

	@Test
	void shouldSkipAuthForFrontHistoryRoutes() {
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/front/model-config")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/front/agent/1/run")).isTrue();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/front/user-agent/1/run")).isTrue();
	}

	@Test
	void shouldNotSkipAuthForApiEndpoints() {
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/api/agent/list")).isFalse();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/api/sessions")).isFalse();
		assertThat(JwtAuthenticationWebFilter.shouldSkipAuth("/agents")).isFalse();
	}

	private String signedHs256Token(String headerJson, String payloadJson, String base64Secret) {
		String header = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
		String payload = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		String content = header + "." + payload;
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256"));
			String signature = Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
			return content + "." + signature;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
