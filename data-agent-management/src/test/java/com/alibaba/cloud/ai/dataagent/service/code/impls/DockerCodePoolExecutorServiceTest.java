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
package com.alibaba.cloud.ai.dataagent.service.code.impls;

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService.TaskRequest;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService.TaskResponse;
import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DockerCodePoolExecutorServiceTest {

	@Mock
	private DockerClient dockerClient;

	private CodeExecutorProperties properties;

	@BeforeEach
	void setUp() {
		properties = new CodeExecutorProperties();
		properties.setContainerNamePrefix("test-docker-");
		properties.setTaskQueueSize(2);
		properties.setCoreContainerNum(1);
		properties.setTempContainerNum(1);
		properties.setCoreThreadSize(1);
		properties.setMaxThreadSize(1);
		properties.setThreadQueueSize(2);
	}

	@Test
	void constructor_withInjectedClient_hasNoDockerSideEffects() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		verifyNoInteractions(dockerClient);

		service.close();
	}

	@Test
	void execute_missingContainerWorkDirectory_returnsSpecificFailure() {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		TaskResponse response = service.execTaskInContainer(new TaskRequest("print(1)", "", ""), "missing");

		assertFalse(response.isSuccess());
		assertEquals("An exception occurred while executing the task: Container 'missing' does not exist work dir",
				response.exceptionMsg());
		service.close();
	}

	@Test
	void close_calledTwice_closesDockerClientExactlyOnce() throws IOException {
		DockerCodePoolExecutorService service = new DockerCodePoolExecutorService(properties, dockerClient, false);

		service.close();
		service.close();

		verify(dockerClient).close();
	}

}
