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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper {

	/**
	 * Query session list by agent ID
	 */
	@Select("""
			<script>
			SELECT * FROM chat_session
			WHERE agent_id = #{agentId} AND status != 'deleted'
			<if test="userId != null">AND user_id = #{userId}</if>
			ORDER BY is_pinned DESC, update_time DESC
			</script>
			""")
	List<ChatSession> selectByAgentId(@Param("agentId") Integer agentId, @Param("userId") String userId);

	/**
	 * Query session details by session ID
	 */
	@Select("""
			<script>
			SELECT * FROM chat_session
			WHERE id = #{sessionId} AND status != 'deleted'
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	ChatSession selectBySessionId(@Param("sessionId") String sessionId, @Param("userId") String userId);

	/**
	 * Update session
	 */
	@Update("""
			<script>
			UPDATE chat_session
			<set>
				<if test="title != null">title = #{title},</if>
				<if test="status != null">status = #{status},</if>
				<if test="isPinned != null">is_pinned = #{isPinned},</if>
				<if test="userId != null">user_id = #{userId},</if>
				update_time = NOW()
			</set>
			WHERE id = #{sessionId}
			</script>
			""")
	int updateById(ChatSession session);

	/**
	 * Soft delete all sessions for an agent
	 */
	@Update("""
			<script>
			UPDATE chat_session
			SET status = 'deleted', update_time = #{updateTime}
			WHERE agent_id = #{agentId}
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	int softDeleteByAgentId(@Param("agentId") Integer agentId, @Param("userId") String userId,
			@Param("updateTime") LocalDateTime updateTime);

	/**
	 * Update session time
	 */
	@Update("""
			<script>
			UPDATE chat_session
			SET update_time = #{updateTime}
			WHERE id = #{sessionId}
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	int updateSessionTime(@Param("sessionId") String sessionId, @Param("userId") String userId,
			@Param("updateTime") LocalDateTime updateTime);

	/**
	 * Update session pinned status
	 */
	@Update("""
			<script>
			UPDATE chat_session SET
				is_pinned = #{isPinned},
				update_time = #{updateTime}
			WHERE id = #{sessionId}
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	int updatePinStatus(@Param("sessionId") String sessionId, @Param("userId") String userId,
			@Param("isPinned") boolean isPinned, @Param("updateTime") LocalDateTime updateTime);

	/**
	 * Update session title
	 */
	@Update("""
			<script>
			UPDATE chat_session SET
				title = #{title},
				update_time = #{updateTime}
			WHERE id = #{sessionId}
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	int updateTitle(@Param("sessionId") String sessionId, @Param("userId") String userId, @Param("title") String title,
			@Param("updateTime") LocalDateTime updateTime);

	/**
	 * Soft delete session
	 */
	@Update("""
			<script>
			UPDATE chat_session
			SET status = 'deleted', update_time = #{updateTime}
			WHERE id = #{sessionId}
			<if test="userId != null">AND user_id = #{userId}</if>
			</script>
			""")
	int softDeleteById(@Param("sessionId") String sessionId, @Param("userId") String userId,
			@Param("updateTime") LocalDateTime updateTime);

	@Insert("""
			INSERT INTO chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time, options)
			VALUES (#{id}, #{agentId}, #{title}, #{status}, #{isPinned}, #{userId}, #{createTime}, #{updateTime}, #{options})
			""")
	int insert(ChatSession session);

}
