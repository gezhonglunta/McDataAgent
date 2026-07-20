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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DBConnectionPoolFactoryTest {

	@Test
	void resolvesPoolFromPrebuiltDatasourceTypeIndex() {
		DBConnectionPool mysql = new StubDBConnectionPool("mysql");
		DBConnectionPool h2 = new StubDBConnectionPool("h2");
		DBConnectionPoolFactory factory = new DBConnectionPoolFactory(List.of(mysql, h2));

		assertEquals(mysql, factory.getPoolByDbType("mysql"));
		assertEquals(h2, factory.getPoolByDbType("h2"));
	}

	@Test
	void unknownPool_failsClearly() {
		DBConnectionPoolFactory factory = new DBConnectionPoolFactory(List.of());

		assertThrows(IllegalStateException.class, () -> factory.getPoolByDbType("unknown"));
	}

	@Test
	void duplicatePool_failsAtStartup() {
		assertThrows(IllegalStateException.class, () -> new DBConnectionPoolFactory(
				List.of(new StubDBConnectionPool("mysql"), new StubDBConnectionPool("mysql"))));
	}

	private record StubDBConnectionPool(String type) implements DBConnectionPool {

		@Override
		public ErrorCodeEnum ping(DbConfigBO config) {
			return null;
		}

		@Override
		public Connection getConnection(DbConfigBO config) {
			return null;
		}

		@Override
		public boolean supportedDataSourceType(String candidate) {
			return type.equals(candidate);
		}

		@Override
		public String getConnectionPoolType() {
			return type;
		}

		@Override
		public void close() {
		}

	}

}
