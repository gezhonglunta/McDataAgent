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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSessionServiceImplTest {

	@Test
	void sessionBelongsToUserReturnsTrueForMatchingUser() {
		ChatSessionMapper mapper = mock(ChatSessionMapper.class);
		ChatSession session = ChatSession.builder().id("session-1").userId("user-a").build();
		when(mapper.selectBySessionId("session-1")).thenReturn(session);

		ChatSessionServiceImpl service = new ChatSessionServiceImpl(mapper);

		assertThat(service.sessionBelongsToUser("session-1", "user-a")).isTrue();
	}

	@Test
	void sessionBelongsToUserReturnsFalseForDifferentUser() {
		ChatSessionMapper mapper = mock(ChatSessionMapper.class);
		ChatSession session = ChatSession.builder().id("session-1").userId("user-a").build();
		when(mapper.selectBySessionId("session-1")).thenReturn(session);

		ChatSessionServiceImpl service = new ChatSessionServiceImpl(mapper);

		assertThat(service.sessionBelongsToUser("session-1", "user-b")).isFalse();
	}

	@Test
	void sessionBelongsToUserAllowsAllSessionsWhenUserIdIsNull() {
		ChatSessionMapper mapper = mock(ChatSessionMapper.class);
		ChatSession session = ChatSession.builder().id("session-1").userId("user-a").build();
		when(mapper.selectBySessionId("session-1")).thenReturn(session);

		ChatSessionServiceImpl service = new ChatSessionServiceImpl(mapper);

		assertThat(service.sessionBelongsToUser("session-1", null)).isTrue();
	}

}
