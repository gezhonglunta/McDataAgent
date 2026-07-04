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
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerClientFactory;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerExecutorFactory;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerHostResolver;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerImageManager;
import com.alibaba.cloud.ai.dataagent.service.code.docker.ZerodepDockerClientConnector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Opt-in smoke test for a real Docker daemon. Maven Surefire does not select the
 * {@code *IT} suffix by default. Run explicitly with
 * {@code ./mvnw -pl data-agent-management -Dtest=DockerCodePoolExecutorServiceIT test}.
 */
@Tag("docker-integration")
class DockerCodePoolExecutorServiceIT {

	@Test
	void create_withAvailableDaemon_preparesAndClosesExecutor() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		properties.setImageName("alpine:3.20");
		DockerHostResolver resolver = new DockerHostResolver();
		DockerExecutorFactory factory = new DockerExecutorFactory(resolver,
				new DockerClientFactory(new ZerodepDockerClientConnector()), new DockerImageManager());

		try (DockerCodePoolExecutorService service = factory.create(properties)) {
			assertNotNull(service);
		}
	}

}
