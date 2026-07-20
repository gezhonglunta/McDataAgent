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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * @author vlsmb
 * @since 2025/9/27
 */
@Component
public class AccessorFactory {

	private final Map<BizDataSourceTypeEnum, Accessor> accessorsByType;

	public AccessorFactory(List<Accessor> accessors) {
		EnumMap<BizDataSourceTypeEnum, Accessor> index = new EnumMap<>(BizDataSourceTypeEnum.class);
		for (BizDataSourceTypeEnum type : BizDataSourceTypeEnum.values()) {
			List<Accessor> matching = accessors.stream()
				.filter(accessor -> accessor.supportedDataSourceType(type))
				.toList();
			if (matching.size() > 1) {
				throw new IllegalStateException("Multiple accessors registered for datasource type: " + type);
			}
			if (!matching.isEmpty()) {
				index.put(type, matching.get(0));
			}
		}
		this.accessorsByType = Map.copyOf(index);
	}

	public Accessor getAccessorByDbConfig(DbConfigBO dbConfig) {
		if (dbConfig == null) {
			throw new IllegalArgumentException("dbConfig cannot be null");
		}
		BizDataSourceTypeEnum typeEnum = Arrays.stream(BizDataSourceTypeEnum.values())
			.filter(e -> e.getDialect().equalsIgnoreCase(dbConfig.getDialectType()))
			.filter(e -> e.getProtocol().equalsIgnoreCase(dbConfig.getConnectionType()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"no accessor registered for dialect: " + dbConfig.getDialectType()));
		return getAccessorByDbTypeEnum(typeEnum);
	}

	public Accessor getAccessorByDbTypeEnum(BizDataSourceTypeEnum typeEnum) {
		Accessor accessor = accessorsByType.get(typeEnum);
		if (accessor == null) {
			throw new IllegalStateException("no accessor registered for datasource type: " + typeEnum);
		}
		return accessor;
	}

}
