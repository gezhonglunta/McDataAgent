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
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@AllArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

	private final ChatSessionMapper chatSessionMapper;

	private final ChatMemory chatMemory;

	/**
	 * Get session list by agent ID
	 */
	@Override
	public List<ChatSession> findByAgentId(Integer agentId, String userId) {
		return chatSessionMapper.selectByAgentId(agentId, userId);
	}

	@Override
	public ChatSession findBySessionId(String sessionId, String userId) {
		return chatSessionMapper.selectBySessionId(sessionId, userId);
	}

	/**
	 * Create a new session
	 */
	@Override
	public ChatSession createSession(Integer agentId, String title, String userId, String options) {
		String sessionId = UUID.randomUUID().toString();

		ChatSession session = new ChatSession(sessionId, agentId, title != null ? title : "新会话", "active", userId);
		session.setOptions(options);
		LocalDateTime now = LocalDateTime.now();
		session.setCreateTime(now);
		session.setUpdateTime(now);
		chatSessionMapper.insert(session);

		log.info("Created new chat session: {} for agent: {}", sessionId, agentId);
		return session;
	}

	/**
	 * Clear all sessions for an agent
	 */
	@Override
	public void clearSessionsByAgentId(Integer agentId, String userId) {
		List<ChatSession> sessions = chatSessionMapper.selectByAgentId(agentId, userId);
		LocalDateTime now = LocalDateTime.now();
		int updated = chatSessionMapper.softDeleteByAgentId(agentId, userId, now);
		sessions.forEach(session -> chatMemory.clear(session.getId()));
		log.info("Cleared {} sessions for agent: {}", updated, agentId);
	}

	/**
	 * Update the last activity time of a session
	 */
	@Override
	public void updateSessionTime(String sessionId, String userId) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateSessionTime(sessionId, userId, now);
	}

	/**
	 * 置顶/取消置顶会话
	 */
	@Override
	public void pinSession(String sessionId, String userId, boolean isPinned) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updatePinStatus(sessionId, userId, isPinned, now);
		log.info("Updated pin status for session: {} to: {}", sessionId, isPinned);
	}

	/**
	 * Rename session
	 */
	@Override
	public void renameSession(String sessionId, String userId, String newTitle) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.updateTitle(sessionId, userId, newTitle, now);
		log.info("Renamed session: {} to: {}", sessionId, newTitle);
	}

	/**
	 * Delete a single session
	 */
	@Override
	public void deleteSession(String sessionId, String userId) {
		LocalDateTime now = LocalDateTime.now();
		chatSessionMapper.softDeleteById(sessionId, userId, now);
		chatMemory.clear(sessionId);
		log.info("Deleted session: {}", sessionId);
	}

}
