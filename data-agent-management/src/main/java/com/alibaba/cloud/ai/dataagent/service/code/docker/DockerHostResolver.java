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
package com.alibaba.cloud.ai.dataagent.service.code.docker;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves Docker connection candidates and classifies their network location.
 */
@Component
public class DockerHostResolver {

	private static final String UNIX_SOCKET = "unix:///var/run/docker.sock";

	private static final String WINDOWS_NAMED_PIPE = "npipe://./pipe/docker_engine";

	private static final String LOCAL_TCP = "tcp://localhost:2375";

	public List<String> candidates(String configuredHost, String osName) {
		String normalizedOs = Objects.requireNonNullElse(osName, "").toLowerCase(Locale.ROOT);
		if (normalizedOs.contains("win")) {
			LinkedHashSet<String> hosts = new LinkedHashSet<>();
			if (StringUtils.hasText(configuredHost)) {
				hosts.add(configuredHost);
			}
			hosts.add(WINDOWS_NAMED_PIPE);
			hosts.add(LOCAL_TCP);
			return List.copyOf(hosts);
		}
		if (StringUtils.hasText(configuredHost)) {
			return List.of(configuredHost);
		}
		return List.of(UNIX_SOCKET);
	}

	public boolean isRemote(String host) {
		if (!StringUtils.hasText(host)) {
			return false;
		}

		URI uri;
		try {
			uri = URI.create(host);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid Docker host URI: " + host, exception);
		}

		String scheme = uri.getScheme();
		if ("unix".equalsIgnoreCase(scheme) || "npipe".equalsIgnoreCase(scheme)) {
			return false;
		}
		if (!"tcp".equalsIgnoreCase(scheme) || !StringUtils.hasText(uri.getHost())) {
			throw new IllegalArgumentException("Unsupported Docker host URI: " + host);
		}

		String hostname = uri.getHost();
		if (hostname.startsWith("[") && hostname.endsWith("]")) {
			hostname = hostname.substring(1, hostname.length() - 1);
		}
		return !("localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname) || "::1".equals(hostname));
	}

}
