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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridSearchRequestTest {

	@Test
	void builder_withoutOverrides_usesSameDefaultsAsNoArgsConstruction() {
		HybridSearchRequest direct = new HybridSearchRequest();
		HybridSearchRequest built = HybridSearchRequest.builder().build();

		assertAll(() -> assertEquals(direct.getSimilarityThreshold(), built.getSimilarityThreshold()),
				() -> assertEquals(direct.getVectorWeight(), built.getVectorWeight()),
				() -> assertEquals(direct.getKeywordWeight(), built.getKeywordWeight()),
				() -> assertEquals(direct.isUseRerank(), built.isUseRerank()),
				() -> assertNotSame(direct.getExtraParams(), built.getExtraParams()));
	}

	@Test
	void builder_withOverrides_preservesExplicitValues() {
		HybridSearchRequest request = HybridSearchRequest.builder()
			.similarityThreshold(0.75)
			.vectorWeight(0.8)
			.keywordWeight(0.2)
			.useRerank(true)
			.build();

		assertAll(() -> assertEquals(0.75, request.getSimilarityThreshold()),
				() -> assertEquals(0.8, request.getVectorWeight()), () -> assertEquals(0.2, request.getKeywordWeight()),
				() -> assertTrue(request.isUseRerank()));
	}

}
