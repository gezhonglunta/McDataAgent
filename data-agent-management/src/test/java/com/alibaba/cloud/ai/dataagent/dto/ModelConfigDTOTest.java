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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelConfigDTOTest {

	@Test
	void builder_withoutOverrides_usesSameDefaultsAsNoArgsConstruction() {
		ModelConfigDTO direct = new ModelConfigDTO();
		ModelConfigDTO built = ModelConfigDTO.builder().build();

		assertAll(() -> assertEquals(direct.getTemperature(), built.getTemperature()),
				() -> assertEquals(direct.getMaxTokens(), built.getMaxTokens()),
				() -> assertEquals(direct.getIsActive(), built.getIsActive()),
				() -> assertEquals(direct.getProxyEnabled(), built.getProxyEnabled()));
	}

	@Test
	void builder_withOverrides_preservesExplicitValues() {
		ModelConfigDTO built = ModelConfigDTO.builder()
			.temperature(0.7)
			.maxTokens(4096)
			.isActive(false)
			.proxyEnabled(true)
			.build();

		assertAll(() -> assertEquals(0.7, built.getTemperature()), () -> assertEquals(4096, built.getMaxTokens()),
				() -> assertFalse(built.getIsActive()), () -> assertTrue(built.getProxyEnabled()));
	}

}
