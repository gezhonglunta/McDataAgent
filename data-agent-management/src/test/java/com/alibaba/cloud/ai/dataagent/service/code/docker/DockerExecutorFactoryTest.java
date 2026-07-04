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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerClientFactory.DockerConnection;
import com.alibaba.cloud.ai.dataagent.service.code.impls.DockerCodePoolExecutorService;
import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerExecutorFactoryTest {

	private static final String REMOTE = "tcp://docker.example:2375";

	private static final String LOCAL = "tcp://localhost:2375";

	@Mock
	private DockerHostResolver hostResolver;

	@Mock
	private DockerClientFactory clientFactory;

	@Mock
	private DockerImageManager imageManager;

	@Mock
	private DockerClient client;

	@Test
	void create_remotePrimaryFallsBackToLocal_usesConnectedHostClassification() {
		CodeExecutorProperties properties = properties();
		List<String> candidates = List.of(REMOTE, LOCAL);
		when(hostResolver.candidates(REMOTE, System.getProperty("os.name"))).thenReturn(candidates);
		when(clientFactory.connect(candidates)).thenReturn(new DockerConnection(client, LOCAL));
		when(hostResolver.isRemote(LOCAL)).thenReturn(false);

		DockerCodePoolExecutorService executor = new DockerExecutorFactory(hostResolver, clientFactory, imageManager)
			.create(properties);

		verify(hostResolver).isRemote(LOCAL);
		executor.close();
	}

	@Test
	void create_imagePreparationFails_closesClientAndRethrowsOriginalFailure() throws IOException {
		CodeExecutorProperties properties = properties();
		List<String> candidates = List.of(REMOTE);
		IllegalStateException imageFailure = new IllegalStateException("pull failed");
		when(hostResolver.candidates(REMOTE, System.getProperty("os.name"))).thenReturn(candidates);
		when(clientFactory.connect(candidates)).thenReturn(new DockerConnection(client, REMOTE));
		org.mockito.Mockito.doThrow(imageFailure).when(imageManager).ensureAvailable(client, properties.getImageName());

		IllegalStateException actual = assertThrows(IllegalStateException.class,
				() -> new DockerExecutorFactory(hostResolver, clientFactory, imageManager).create(properties));

		assertSame(imageFailure, actual);
		InOrder order = inOrder(imageManager, client);
		order.verify(imageManager).ensureAvailable(client, properties.getImageName());
		order.verify(client).close();
	}

	private CodeExecutorProperties properties() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setHost(REMOTE);
		properties.setTaskQueueSize(2);
		properties.setCoreContainerNum(1);
		properties.setTempContainerNum(1);
		properties.setCoreThreadSize(1);
		properties.setMaxThreadSize(1);
		properties.setThreadQueueSize(2);
		return properties;
	}

}
