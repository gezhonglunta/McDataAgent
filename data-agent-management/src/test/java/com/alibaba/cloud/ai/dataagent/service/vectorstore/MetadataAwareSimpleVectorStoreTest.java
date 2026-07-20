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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class MetadataAwareSimpleVectorStoreTest {

	@Test
	void deleteByMetadata_doesNotInvokeEmbeddingModel() {
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(2);
		when(embeddingModel.embed(any(Document.class))).thenReturn(new float[] { 1.0f, 0.0f });
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(embeddingModel);
		store.add(List.of(new Document("one", Map.of("agentId", "1")), new Document("two", Map.of("agentId", "2"))));

		reset(embeddingModel);
		when(embeddingModel.dimensions()).thenThrow(new IllegalStateException("embedding unavailable"));

		assertEquals(1, store.deleteByMetadata(Map.of("agentId", 1)));
		assertEquals(0, store.deleteByMetadata(Map.of("agentId", "1")));
		assertEquals(1, store.deleteByMetadata(Map.of("agentId", "2")));
	}

}
