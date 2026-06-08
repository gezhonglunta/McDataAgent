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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.util.UserContextHolder;
import com.alibaba.cloud.ai.dataagent.service.chat.SessionEventPublisher;
import com.alibaba.cloud.ai.dataagent.vo.SessionUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import org.springframework.http.server.reactive.ServerHttpResponse;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionEventController {

	private final SessionEventPublisher sessionEventPublisher;

	@GetMapping(value = "/agent/{agentId}/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<SessionUpdateEvent>> streamSessionUpdates(@PathVariable Integer agentId,
			ServerHttpResponse response, ServerWebExchange exchange) {
		response.getHeaders().add("Cache-Control", "no-cache");
		response.getHeaders().add("Connection", "keep-alive");
		response.getHeaders().add("Access-Control-Allow-Origin", "*");

		String userId = UserContextHolder.getCurrentUserId(exchange);
		log.info("Session SSE subscribe request: agentId={}, userId={}, path={}", agentId, maskUserId(userId),
				exchange.getRequest().getURI().getPath());
		log.debug("Client subscribed to session update stream for agent {}, user {}", agentId, userId);
		return sessionEventPublisher.register(agentId, userId)
			.doFinally(
					signal -> log.debug("Session update stream finished for agent {} with signal {}", agentId, signal));
	}

	private String maskUserId(String userId) {
		if (userId == null) {
			return "null";
		}
		if (userId.isEmpty()) {
			return "empty";
		}
		if (userId.length() <= 6) {
			return "***";
		}
		return userId.substring(0, 3) + "***" + userId.substring(userId.length() - 3);
	}

}
