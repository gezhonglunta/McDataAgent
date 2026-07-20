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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DdlFactoryTest {

	@Test
	void resolvesDdlFromDatasourceTypeAndConfig() {
		Ddl mysql = mock(Ddl.class);
		when(mysql.getDataSourceType()).thenReturn(BizDataSourceTypeEnum.MYSQL);
		DdlFactory factory = new DdlFactory(List.of(mysql));
		DbConfigBO config = new DbConfigBO();
		config.setDialectType("mysql");

		assertEquals(mysql, factory.getDdlExecutorByDbType(BizDataSourceTypeEnum.MYSQL));
		assertEquals(mysql, factory.getDdlExecutorByDbConfig(config));
	}

	@Test
	void missingDdl_failsClearly() {
		DdlFactory factory = new DdlFactory(List.of());

		assertThrows(IllegalStateException.class,
				() -> factory.getDdlExecutorByDbType(BizDataSourceTypeEnum.POSTGRESQL));
	}

	@Test
	void duplicateDdl_failsAtStartup() {
		Ddl first = mock(Ddl.class);
		Ddl duplicate = mock(Ddl.class);
		when(first.getDataSourceType()).thenReturn(BizDataSourceTypeEnum.MYSQL);
		when(duplicate.getDataSourceType()).thenReturn(BizDataSourceTypeEnum.MYSQL);

		assertThrows(IllegalStateException.class, () -> new DdlFactory(List.of(first, duplicate)));
	}

}
