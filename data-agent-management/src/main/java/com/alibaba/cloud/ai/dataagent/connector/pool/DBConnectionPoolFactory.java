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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * DB connection pool factory
 */
@Component
public class DBConnectionPoolFactory {

	private final Map<BizDataSourceTypeEnum, DBConnectionPool> poolsByType;

	public DBConnectionPoolFactory(List<DBConnectionPool> pools) {
		EnumMap<BizDataSourceTypeEnum, DBConnectionPool> index = new EnumMap<>(BizDataSourceTypeEnum.class);
		for (BizDataSourceTypeEnum type : BizDataSourceTypeEnum.values()) {
			List<DBConnectionPool> matching = pools.stream()
				.filter(pool -> pool.supportedDataSourceType(type.getTypeName()))
				.toList();
			if (matching.size() > 1) {
				throw new IllegalStateException("Multiple connection pools registered for datasource type: " + type);
			}
			if (!matching.isEmpty()) {
				index.put(type, matching.get(0));
			}
		}
		this.poolsByType = Map.copyOf(index);
	}

	public DBConnectionPool getPoolByDbType(String type) {
		BizDataSourceTypeEnum datasourceType = BizDataSourceTypeEnum.fromTypeName(type);
		DBConnectionPool pool = datasourceType == null ? null : poolsByType.get(datasourceType);
		if (pool == null) {
			throw new IllegalStateException("No DB connection pool found for type: " + type);
		}
		return pool;
	}

}
