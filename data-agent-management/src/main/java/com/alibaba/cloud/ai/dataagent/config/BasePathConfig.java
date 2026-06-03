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
package com.alibaba.cloud.ai.dataagent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

public class BasePathConfig implements EnvironmentPostProcessor, Ordered {

	private static final String DATA_AGENT_BASE_PATH = "spring.ai.alibaba.data-agent.base-path";

	private static final String WEBFLUX_BASE_PATH = "spring.webflux.base-path";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (StringUtils.hasText(environment.getProperty(WEBFLUX_BASE_PATH))) {
			return;
		}
		String basePath = environment.getProperty(DATA_AGENT_BASE_PATH);
		if (!StringUtils.hasText(basePath)) {
			return;
		}
		environment.getPropertySources()
			.addFirst(new MapPropertySource("dataAgentBasePath", Map.of(WEBFLUX_BASE_PATH, normalize(basePath))));
	}

	@Override
	public int getOrder() {
		return ConfigDataEnvironmentPostProcessor.ORDER + 1;
	}

	private String normalize(String basePath) {
		String normalized = basePath.trim();
		if (!normalized.startsWith("/")) {
			normalized = "/" + normalized;
		}
		if (normalized.length() > 1 && normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

}
