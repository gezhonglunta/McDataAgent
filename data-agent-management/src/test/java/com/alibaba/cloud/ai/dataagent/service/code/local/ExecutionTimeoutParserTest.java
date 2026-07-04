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
package com.alibaba.cloud.ai.dataagent.service.code.local;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionTimeoutParserTest {

	private static final long DEFAULT_TIMEOUT_MILLIS = 60_000;

	private final ExecutionTimeoutParser parser = new ExecutionTimeoutParser();

	@ParameterizedTest
	@CsvSource({ "500ms,500", "0s,0", "30s,30000", "2m,120000", "1h,3600000", "1d,86400000", "100S,100000" })
	void parse_validValue_returnsMilliseconds(String value, long expected) {
		assertEquals(expected, parser.parse(value));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "invalid", "30s trailing", "-1s", "9223372036854775807d" })
	void parse_invalidOrOverflowingValue_returnsDocumentedDefault(String value) {
		assertEquals(DEFAULT_TIMEOUT_MILLIS, parser.parse(value));
	}

}
