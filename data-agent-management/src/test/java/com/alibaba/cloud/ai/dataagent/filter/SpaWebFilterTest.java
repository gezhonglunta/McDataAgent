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
package com.alibaba.cloud.ai.dataagent.filter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpaWebFilterTest {

	@Test
	void supportsRootHistoryRouteWhenGatewayStripsPrefix() {
		assertThat(SpaWebFilter.shouldFallbackToIndex("/agents", "")).isTrue();
		assertThat(SpaWebFilter.shouldFallbackToIndex("/agent/1/run", "")).isTrue();
	}

	@Test
	void keepsFrontHistoryRouteCompatibility() {
		assertThat(SpaWebFilter.shouldFallbackToIndex("/front/agents", "")).isTrue();
	}

	@Test
	void excludesApiUploadsAndStaticAssets() {
		assertThat(SpaWebFilter.shouldFallbackToIndex("/api/agent/list", "")).isFalse();
		assertThat(SpaWebFilter.shouldFallbackToIndex("/uploads/avatar.png", "")).isFalse();
		assertThat(SpaWebFilter.shouldFallbackToIndex("/assets/index.js", "")).isFalse();
		assertThat(SpaWebFilter.shouldFallbackToIndex("/front/assets/index.js", "")).isFalse();
	}

}
