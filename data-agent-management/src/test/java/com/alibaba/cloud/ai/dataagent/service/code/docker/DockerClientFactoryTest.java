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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PingCmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerClientFactoryTest {

	private static final String PRIMARY = "npipe://./pipe/docker_engine";

	private static final String FALLBACK = "tcp://localhost:2375";

	@Mock
	private DockerClientConnector connector;

	@Mock
	private DockerClient primaryClient;

	@Mock
	private DockerClient fallbackClient;

	@Mock
	private PingCmd primaryPing;

	@Mock
	private PingCmd fallbackPing;

	@Test
	void create_primaryCandidateWorks_returnsItWithoutTryingFallback() {
		when(connector.connect(PRIMARY)).thenReturn(primaryClient);
		when(primaryClient.pingCmd()).thenReturn(primaryPing);

		DockerClient result = new DockerClientFactory(connector).create(List.of(PRIMARY, FALLBACK));

		assertSame(primaryClient, result);
		verify(primaryPing).exec();
		verify(connector, never()).connect(FALLBACK);
	}

	@Test
	void create_primaryPingFails_closesItThenUsesFallback() throws IOException {
		when(connector.connect(PRIMARY)).thenReturn(primaryClient);
		when(primaryClient.pingCmd()).thenReturn(primaryPing);
		when(primaryPing.exec()).thenThrow(new IllegalStateException("pipe unavailable"));
		when(connector.connect(FALLBACK)).thenReturn(fallbackClient);
		when(fallbackClient.pingCmd()).thenReturn(fallbackPing);

		DockerClient result = new DockerClientFactory(connector).create(List.of(PRIMARY, FALLBACK));

		assertSame(fallbackClient, result);
		InOrder order = inOrder(primaryClient, connector);
		order.verify(primaryClient).close();
		order.verify(connector).connect(FALLBACK);
		verify(fallbackPing).exec();
	}

	@Test
	void create_allCandidatesFail_reportsEveryHostAndLastCause() {
		when(connector.connect(PRIMARY)).thenThrow(new IllegalStateException("pipe unavailable"));
		when(connector.connect(FALLBACK)).thenThrow(new IllegalStateException("tcp unavailable"));

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new DockerClientFactory(connector).create(List.of(PRIMARY, FALLBACK)));

		assertTrue(error.getMessage().contains(PRIMARY));
		assertTrue(error.getMessage().contains(FALLBACK));
		assertTrue(error.getCause().getMessage().contains("tcp unavailable"));
	}

	@Test
	void create_withoutCandidates_rejectsInvalidConfiguration() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> new DockerClientFactory(connector).create(List.of()));

		assertTrue(error.getMessage().contains("candidate"));
	}

}
