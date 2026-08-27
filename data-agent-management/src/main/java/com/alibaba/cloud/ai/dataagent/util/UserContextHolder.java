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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.dto.JwtUser;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import org.springframework.web.server.ServerWebExchange;

public class UserContextHolder {

	private static final String USER_ATTRIBUTE_KEY = JwtUser.class.getName();

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

	public static void write(ServerWebExchange exchange, JwtUser user) {
		exchange.getAttributes().put(USER_ATTRIBUTE_KEY, user);
	}

	public static String getCurrentUserId(ServerWebExchange exchange) {
		Object user = exchange.getAttribute(USER_ATTRIBUTE_KEY);
		if (user instanceof JwtUser jwtUser) {
			return jwtUser.getUserId();
		}
		return getCurrentUserId();
	}

	public static void clear() {
		THREAD_LOCAL.remove();
	}

}
