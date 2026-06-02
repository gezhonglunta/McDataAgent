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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.search.VectorStoreSearchTestRequest;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/test/vector-store")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class VectorStoreTestController {

	private final VectorStore vectorStore;

	@PostMapping("/search")
	public ApiResponse<List<Document>> search(@RequestBody VectorStoreSearchTestRequest request) {
		try {
			SearchRequest.Builder builder = SearchRequest.builder().query(request.getQuery());

			if (request.getTopK() != null) {
				builder.topK(request.getTopK());
			}
			if (request.getSimilarityThreshold() != null) {
				builder.similarityThreshold(request.getSimilarityThreshold());
			}
			if (StringUtils.isNotBlank(request.getFilterExpression())) {
				Filter.Expression expression = new FilterExpressionTextParser().parse(request.getFilterExpression());
				builder.filterExpression(expression);
			}

			List<Document> documents = vectorStore.similaritySearch(builder.build());
			return ApiResponse.success("success search vector store", documents);
		} catch (Exception e) {
			log.error("Failed to test vector store search", e);
			return ApiResponse.error("Failed to search vector store: " + e.getMessage());
		}
	}

}
