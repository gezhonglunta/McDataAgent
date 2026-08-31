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

import com.alibaba.cloud.ai.dataagent.entity.McUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface McUserMapper {

	/**
	 * Query user by user ID
	 */
	@Select("""
			SELECT * FROM mc_user
			WHERE user_id = #{userId}
			""")
	McUser selectByUserId(@Param("userId") Long userId);

	/**
	 * Query user by MC user ID
	 */
	@Select("""
			SELECT * FROM mc_user
			WHERE mc_user_id = #{mcUserId}
			""")
	McUser selectByMcUserId(@Param("mcUserId") String mcUserId);

	@Insert("""
			INSERT INTO mc_user (mc_user_id, create_time)
			VALUES (#{mcUserId}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "userId", keyColumn = "user_id")
	int insert(McUser user);

	@Delete("""
			DELETE FROM mc_user
			WHERE user_id = #{userId}
			""")
	int deleteByUserId(@Param("userId") Long userId);

	@Delete("""
			DELETE FROM mc_user
			WHERE mc_user_id = #{mcUserId}
			""")
	int deleteByMcUserId(@Param("mcUserId") String mcUserId);

}
