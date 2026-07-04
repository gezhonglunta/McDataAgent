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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerHostResolverTest {

	private final DockerHostResolver resolver = new DockerHostResolver();

	@Test
	void candidates_explicitUnixHost_keepsOnlyConfiguredHost() {
		assertEquals(List.of("tcp://docker.example:2375"), resolver.candidates("tcp://docker.example:2375", "Linux"));
	}

	@Test
	void candidates_explicitWindowsHost_keepsFallbacksWithoutDuplicates() {
		assertEquals(List.of("tcp://docker.example:2375", "npipe://./pipe/docker_engine", "tcp://localhost:2375"),
				resolver.candidates("tcp://docker.example:2375", "Windows 11"));
	}

	@Test
	void candidates_windowsWithoutHost_returnsNamedPipeThenLocalTcp() {
		assertEquals(List.of("npipe://./pipe/docker_engine", "tcp://localhost:2375"),
				resolver.candidates(null, "Windows 11"));
	}

	@Test
	void candidates_unixWithoutHost_returnsUnixSocket() {
		assertEquals(List.of("unix:///var/run/docker.sock"), resolver.candidates(null, "Mac OS X"));
		assertEquals(List.of("unix:///var/run/docker.sock"), resolver.candidates(null, "Linux"));
		assertEquals(List.of("unix:///var/run/docker.sock"), resolver.candidates(null, null));
	}

	@Test
	void isRemote_distinguishesLocalAndRemoteHosts() {
		assertFalse(resolver.isRemote(null));
		assertFalse(resolver.isRemote(""));
		assertFalse(resolver.isRemote("unix:///var/run/docker.sock"));
		assertFalse(resolver.isRemote("npipe://./pipe/docker_engine"));
		assertFalse(resolver.isRemote("tcp://localhost:2375"));
		assertFalse(resolver.isRemote("tcp://127.0.0.1:2375"));
		assertFalse(resolver.isRemote("tcp://[::1]:2375"));
		assertTrue(resolver.isRemote("tcp://docker.example:2375"));
	}

	@Test
	void isRemote_malformedOrUnsupportedHost_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> resolver.isRemote("not a docker uri"));
		assertThrows(IllegalArgumentException.class, () -> resolver.isRemote("http://docker.example:2375"));
	}

}
