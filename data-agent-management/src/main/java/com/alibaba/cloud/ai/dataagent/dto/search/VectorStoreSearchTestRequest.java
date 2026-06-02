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
package com.alibaba.cloud.ai.dataagent.dto.search;

import lombok.Data;

@Data
public class VectorStoreSearchTestRequest {

	/**
	 * Vector search query text.
	 */
	private String query;

	/**
	 * Maximum number of documents to return.
	 */
	private Integer topK;

	/**
	 * Minimum similarity score. Null keeps SearchRequest default.
	 */
	private Double similarityThreshold;

	/**
	 * Spring AI filter expression text, for example: datasourceId == '4' && vectorType == 'TABLE'.
	 */
	private String filterExpression;

}
