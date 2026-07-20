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
package com.alibaba.cloud.ai.dataagent.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyCredentialServiceTest {

	private final ApiKeyCredentialService service = new ApiKeyCredentialService(
			PasswordEncoderFactories.createDelegatingPasswordEncoder());

	@Test
	void encodedCredential_isOneWayAndRetainsOnlyDisplayHint() {
		String raw = "sk-abcdefghijklmnopqrstuvwxyz123456";

		String encoded = service.encode(raw);

		assertNotEquals(raw, encoded);
		assertFalse(encoded.contains(raw));
		assertTrue(service.matches(raw, encoded));
		assertFalse(service.matches("sk-wrong", encoded));
		assertEquals("****3456", service.mask(encoded));
	}

	@Test
	void legacyPlaintextCredential_remainsCompatibleDuringUpgrade() {
		String legacy = "sk-legacy1234";

		assertTrue(service.matches(legacy, legacy));
		assertFalse(service.matches("sk-wrong", legacy));
		assertEquals("****1234", service.mask(legacy));
	}

}
