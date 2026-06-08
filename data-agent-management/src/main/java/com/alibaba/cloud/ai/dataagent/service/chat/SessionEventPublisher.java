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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.vo.SessionUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manage SSE streams that push session updates to frontend. 一个 agent 对应一个共享的
 * sink，多个连接共享同一个 sink。
 */
@Slf4j
@Service
public class SessionEventPublisher {

	private final Map<String, AgentSessionSink> sinks = new ConcurrentHashMap<>();

	private String buildKey(Integer agentId, String userId) {
		return agentId + ":" + (userId != null ? userId : "");
	}

	public Flux<ServerSentEvent<SessionUpdateEvent>> register(Integer agentId, String userId) {
		String key = buildKey(agentId, userId);
		AgentSessionSink sink = sinks.computeIfAbsent(key, k -> new AgentSessionSink());
		Flux<ServerSentEvent<SessionUpdateEvent>> heartbeat = Flux.interval(Duration.ofSeconds(2))
			.map(i -> ServerSentEvent.<SessionUpdateEvent>builder().comment("heartbeat").build());
		sink.increment();
		log.info("Session SSE registered: agentId={}, userId={}, key={}, subscribers={}, activeKeys={}", agentId,
				maskUserId(userId), safeKey(agentId, userId), sink.subscribers.get(), sinks.size());
		log.debug("Registered subscriber for key {}, current count: {}", key, sink.subscribers.get());
		return Flux.merge(heartbeat, sink.sink.asFlux()).doFinally(signalType -> cleanup(key, sink, signalType));
	}

	public void publishTitleUpdated(Integer agentId, String userId, String sessionId, String title) {
		if (agentId == null) {
			log.warn("Session title SSE skipped because agentId is null: sessionId={}, title={}", sessionId, title);
			return;
		}
		String key = buildKey(agentId, userId);
		SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
		AgentSessionSink sink = sinks.get(key);
		log.info("Session title SSE publishing: sessionId={}, agentId={}, userId={}, key={}, hasSink={}, activeKeys={}",
				sessionId, agentId, maskUserId(userId), safeKey(agentId, userId), sink != null, sinks.size());
		if (sink == null) {
			log.warn("Session title SSE skipped: no active subscribers, sessionId={}, key={}, activeKeys={}", sessionId,
					safeKey(agentId, userId), sinks.size());
			log.debug("No active subscribers for key {}, skip pushing session title update", key);
			return;
		}
		Sinks.EmitResult result = sink.sink.tryEmitNext(ServerSentEvent.builder(event).event(event.getType()).build());
		log.info("Session title SSE emitted: sessionId={}, key={}, result={}, subscribers={}", sessionId,
				safeKey(agentId, userId), result, sink.subscribers.get());
		if (result.isFailure()) {
			log.warn("Failed to emit session title update for key {}, session {}, reason {}", key, sessionId, result);
		}
	}

	private void cleanup(String key, AgentSessionSink sink, SignalType signalType) {
		int current = sink.decrement();
		log.info("Session SSE cleanup: key={}, signal={}, remainingSubscribers={}, activeKeys={}", safeKey(key), signalType,
				current, sinks.size());
		log.debug("Cleanup called for key {}, signal: {}, remaining subscribers: {}", key, signalType, current);
		if (current <= 0) {
			if (sinks.remove(key, sink)) {
				sink.sink.tryEmitComplete();
				log.info("Session SSE sink removed: key={}, activeKeys={}", safeKey(key), sinks.size());
				log.debug("Removed session update sink for key {}", key);
			}
		}
	}

	private String safeKey(Integer agentId, String userId) {
		return agentId + ":" + maskUserId(userId);
	}

	private String safeKey(String key) {
		int separatorIndex = key.indexOf(':');
		if (separatorIndex < 0) {
			return key;
		}
		return key.substring(0, separatorIndex + 1) + maskUserId(key.substring(separatorIndex + 1));
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

	private static class AgentSessionSink {

		private final AtomicInteger subscribers = new AtomicInteger(0);

		private final Sinks.Many<ServerSentEvent<SessionUpdateEvent>> sink = Sinks.many()
			.multicast()
			.onBackpressureBuffer();

		private void increment() {
			subscribers.incrementAndGet();
		}

		private int decrement() {
			return subscribers.decrementAndGet();
		}

	}

}
