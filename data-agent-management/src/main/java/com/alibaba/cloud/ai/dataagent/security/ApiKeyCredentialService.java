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

import com.alibaba.cloud.ai.dataagent.util.ApiKeyUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ApiKeyCredentialService {

	private static final String HINT_SEPARATOR = "::";

	private final PasswordEncoder apiKeyPasswordEncoder;

	public String encode(String rawApiKey) {
		return apiKeyPasswordEncoder.encode(rawApiKey) + HINT_SEPARATOR + suffix(rawApiKey);
	}

	public boolean matches(String rawApiKey, String storedCredential) {
		if (!StringUtils.hasText(rawApiKey) || !StringUtils.hasText(storedCredential)) {
			return false;
		}
		if (!storedCredential.startsWith("{")) {
			return MessageDigest.isEqual(rawApiKey.getBytes(StandardCharsets.UTF_8),
					storedCredential.getBytes(StandardCharsets.UTF_8));
		}
		return apiKeyPasswordEncoder.matches(rawApiKey, encodedPart(storedCredential));
	}

	public String mask(String storedCredential) {
		if (!StringUtils.hasText(storedCredential)) {
			return null;
		}
		if (!storedCredential.startsWith("{")) {
			return ApiKeyUtil.mask(storedCredential);
		}
		int separatorIndex = storedCredential.lastIndexOf(HINT_SEPARATOR);
		return separatorIndex < 0 ? "****"
				: "****" + storedCredential.substring(separatorIndex + HINT_SEPARATOR.length());
	}

	private String encodedPart(String storedCredential) {
		int separatorIndex = storedCredential.lastIndexOf(HINT_SEPARATOR);
		return separatorIndex < 0 ? storedCredential : storedCredential.substring(0, separatorIndex);
	}

	private String suffix(String rawApiKey) {
		return rawApiKey.substring(Math.max(0, rawApiKey.length() - 4));
	}

}
