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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry;

import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasourceTypeHandlerRegistryTest {

	@Test
	void getRequired_normalizesType() {
		DatasourceTypeHandler mysql = new StubHandler("mysql");
		DatasourceTypeHandlerRegistry registry = new DatasourceTypeHandlerRegistry(List.of(mysql));

		assertEquals(mysql, registry.getRequired(" MYSQL "));
	}

	@Test
	void getRequired_rejectsBlankAndUnknownTypes() {
		DatasourceTypeHandlerRegistry registry = new DatasourceTypeHandlerRegistry(List.of());

		assertThrows(IllegalArgumentException.class, () -> registry.getRequired(" "));
		assertThrows(IllegalStateException.class, () -> registry.getRequired("oracle"));
	}

	@Test
	void duplicateHandler_failsAtStartup() {
		assertThrows(IllegalStateException.class,
				() -> new DatasourceTypeHandlerRegistry(List.of(new StubHandler("mysql"), new StubHandler("MYSQL"))));
	}

	private record StubHandler(String typeName) implements DatasourceTypeHandler {
	}

}
