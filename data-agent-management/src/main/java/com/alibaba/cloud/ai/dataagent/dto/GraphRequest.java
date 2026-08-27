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
package com.alibaba.cloud.ai.dataagent.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphRequest {

	private String agentId;

	/** Stable chat-memory conversation identifier. */
	private String conversationId;

	/** Graph run identifier. Reused only when resuming human feedback. */
	private String threadId;

	private String query;

	private boolean humanFeedback;

	private String humanFeedbackContent;

	private boolean rejectedPlan;

	private boolean nl2sqlOnly;

	/**
	 * 规范化 conversationId 与 threadId，保证入口（Controller）与服务层使用同一组 ID：
	 * <ul>
	 * <li>新会话：无 conversationId 时用 threadId（兼容旧客户端）或新 UUID 兜底；threadId 每次重新生成。</li>
	 * <li>恢复人工反馈：threadId 必传，缺失时抛异常。</li>
	 * <li>无 conversationId：用 threadId 兜底。</li>
	 * </ul>
	 * 注意：非恢复场景会覆盖已有 threadId（与原行为一致），因此只允许调用一次。
	 */
	public void normalizeIds() {
		boolean resuming = StringUtils.hasText(humanFeedbackContent);
		if (!resuming) {
			if (!StringUtils.hasText(conversationId)) {
				conversationId = StringUtils.hasText(threadId) ? threadId : UUID.randomUUID().toString();
			}
			threadId = UUID.randomUUID().toString();
		}
		else if (!StringUtils.hasText(threadId)) {
			throw new IllegalArgumentException("Graph run ID is required when resuming human feedback");
		}
		if (!StringUtils.hasText(conversationId)) {
			// Compatibility for existing clients: their old threadId was both the
			// conversation ID and graph run ID.
			conversationId = threadId;
		}
	}

}
