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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessorFactoryTest {

	private Accessor mysqlAccessor;

	private AccessorFactory factory;

	@BeforeEach
	void setUp() {
		mysqlAccessor = mock(Accessor.class);
		for (BizDataSourceTypeEnum type : BizDataSourceTypeEnum.values()) {
			when(mysqlAccessor.supportedDataSourceType(type)).thenReturn(type == BizDataSourceTypeEnum.MYSQL);
		}
		factory = new AccessorFactory(List.of(mysqlAccessor));
	}

	@Test
	void getAccessorByDbType_usesPrebuiltTypeIndex() {
		assertEquals(mysqlAccessor, factory.getAccessorByDbTypeEnum(BizDataSourceTypeEnum.MYSQL));
	}

	@Test
	void getAccessorByDbConfig_resolvesDialectAndProtocol() {
		DbConfigBO config = new DbConfigBO();
		config.setDialectType(BizDataSourceTypeEnum.MYSQL.getDialect());
		config.setConnectionType(BizDataSourceTypeEnum.MYSQL.getProtocol());

		assertEquals(mysqlAccessor, factory.getAccessorByDbConfig(config));
	}

	@Test
	void missingAccessor_failsClearly() {
		assertThrows(IllegalStateException.class,
				() -> factory.getAccessorByDbTypeEnum(BizDataSourceTypeEnum.POSTGRESQL));
	}

	@Test
	void duplicateAccessor_failsAtStartup() {
		Accessor duplicate = mock(Accessor.class);
		when(duplicate.supportedDataSourceType(BizDataSourceTypeEnum.MYSQL)).thenReturn(true);

		assertThrows(IllegalStateException.class, () -> new AccessorFactory(List.of(mysqlAccessor, duplicate)));
	}

}
