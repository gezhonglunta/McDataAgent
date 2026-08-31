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

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Access log 过滤器：在请求处理完成后记录方法、URI、状态码、耗时、客户端来源等信息，
 * 通过独立的 ACCESS_LOG logger 输出到 logs/access.log。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogWebFilter implements WebFilter {

	private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long startNanos = System.nanoTime();
		return chain.filter(exchange).doFinally(signal -> {
			long costMillis = (System.nanoTime() - startNanos) / 1_000_000;
			var request = exchange.getRequest();
			ACCESS_LOG.info("{} {} status={} costMs={} clientIp={} userAgent={}",
					request.getMethod() != null ? request.getMethod().name() : "-",
					sanitize(request.getURI().toString()), statusCode(exchange), costMillis,
					clientIp(exchange), sanitize(request.getHeaders().getFirst(HttpHeaders.USER_AGENT)));
		});
	}

	private static int statusCode(ServerWebExchange exchange) {
		HttpStatusCode status = exchange.getResponse().getStatusCode();
		return status != null ? status.value() : HttpStatus.OK.value();
	}

	private static String clientIp(ServerWebExchange exchange) {
		var headers = exchange.getRequest().getHeaders();
		String forwardedFor = headers.getFirst("X-Forwarded-For");
		if (org.springframework.util.StringUtils.hasText(forwardedFor)) {
			String first = forwardedFor.split(",")[0].trim();
			if (org.springframework.util.StringUtils.hasText(first)) {
				return stripPort(first);
			}
		}
		String realIp = headers.getFirst("X-Real-IP");
		if (org.springframework.util.StringUtils.hasText(realIp)) {
			return stripPort(realIp.trim());
		}
		InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
		return remote != null ? remote.getAddress().getHostAddress() : "-";
	}

	private static String stripPort(String ip) {
		if (ip == null || ip.isEmpty()) {
			return "-";
		}
		if (ip.startsWith("[")) {
			int idx = ip.indexOf(']');
			return idx > 0 ? ip.substring(1, idx) : ip;
		}
		int idx = ip.indexOf(':');
		return idx > 0 ? ip.substring(0, idx) : ip;
	}

	private static String sanitize(String value) {
		if (value == null || value.isEmpty()) {
			return "-";
		}
		return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
	}

}
