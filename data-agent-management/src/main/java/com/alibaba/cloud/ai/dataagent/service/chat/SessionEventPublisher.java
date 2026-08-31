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

	private final Map<StreamKey, AgentSessionSink> sinks = new ConcurrentHashMap<>();

	public Flux<ServerSentEvent<SessionUpdateEvent>> register(String userId, Integer agentId) {
		return Flux.defer(() -> {
			StreamKey key = new StreamKey(userId, agentId);
			AgentSessionSink sink = sinks.computeIfAbsent(key, k -> new AgentSessionSink());
			Flux<ServerSentEvent<SessionUpdateEvent>> heartbeat = Flux.interval(Duration.ofSeconds(2))
				.map(i -> ServerSentEvent.<SessionUpdateEvent>builder().comment("heartbeat").build());
			sink.increment();
			log.debug("Registered subscriber for stream {}:{}, current count: {}", userId, agentId,
					sink.subscribers.get());
			return Flux.merge(heartbeat, sink.sink.asFlux())
				.doFinally(signalType -> cleanup(key, sink, signalType));
		});
	}

	public void publishTitleUpdated(String userId, Integer agentId, String sessionId, String title) {
		if (agentId == null) {
			return;
		}
		SessionUpdateEvent event = SessionUpdateEvent.titleUpdated(sessionId, title);
		AgentSessionSink sink = sinks.get(new StreamKey(userId, agentId));
		if (sink == null) {
			log.debug("No active subscribers for stream {}:{}, skip pushing session title update", userId, agentId);
			return;
		}
		Sinks.EmitResult result = sink.sink.tryEmitNext(ServerSentEvent.builder(event).event(event.getType()).build());
		if (result.isFailure()) {
			log.warn("Failed to emit session title update for stream {}:{}, session {}, reason {}", userId, agentId,
					sessionId, result);
		}
	}

	private void cleanup(StreamKey key, AgentSessionSink sink, SignalType signalType) {
		int current = sink.decrement();
		log.debug("Cleanup called for stream {}:{}, signal: {}, remaining subscribers: {}", key.userId(), key.agentId(),
				signalType, current);
		if (current <= 0) {
			// 使用 remove(key, value) 确保只移除当前的 sink 实例，防止并发问题
			if (sinks.remove(key, sink)) {
				sink.sink.tryEmitComplete();
				log.debug("Removed session update sink for stream {}:{}", key.userId(), key.agentId());
			}
		}
	}

	private record StreamKey(String userId, Integer agentId) {
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
