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

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;

import java.util.List;

/**
 * Chat Session Service Class
 */
public interface ChatSessionService {

	List<ChatSession> findByAgentId(Integer agentId, String userId);

	ChatSession createSession(Integer agentId, String title, String userId);

	ChatSession findBySessionId(String sessionId);

	boolean sessionBelongsToUser(String sessionId, String userId);

	void clearSessionsByAgentId(Integer agentId, String userId);

	void updateSessionTime(String sessionId);

	void pinSession(String sessionId, boolean isPinned);

	void renameSession(String sessionId, String newTitle);

	void deleteSession(String sessionId);

	default List<ChatSession> findByAgentId(Integer agentId) {
		return findByAgentId(agentId, null);
	}

	default void clearSessionsByAgentId(Integer agentId) {
		clearSessionsByAgentId(agentId, null);
	}

}
