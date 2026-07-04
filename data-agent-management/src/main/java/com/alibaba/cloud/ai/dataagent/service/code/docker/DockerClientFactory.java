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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Connects to the first reachable Docker endpoint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerClientFactory {

	private final DockerClientConnector connector;

	public DockerClient create(List<String> candidateHosts) {
		return connect(candidateHosts).client();
	}

	public DockerConnection connect(List<String> candidateHosts) {
		if (candidateHosts == null || candidateHosts.isEmpty()) {
			throw new IllegalArgumentException("At least one Docker host candidate is required");
		}

		RuntimeException lastFailure = null;
		for (String host : candidateHosts) {
			DockerClient client = null;
			try {
				client = connector.connect(host);
				client.pingCmd().exec();
				log.info("Connected to Docker using {}", host);
				return new DockerConnection(client, host);
			}
			catch (RuntimeException exception) {
				lastFailure = exception;
				closeFailedClient(client, exception);
				log.warn("Could not connect to Docker using {}: {}", host, exception.getMessage());
			}
		}

		throw new IllegalStateException("Failed to connect to Docker. Attempted hosts: " + candidateHosts, lastFailure);
	}

	public record DockerConnection(DockerClient client, String host) {
	}

	private void closeFailedClient(DockerClient client, RuntimeException connectionFailure) {
		if (client == null) {
			return;
		}
		try {
			client.close();
		}
		catch (IOException closeFailure) {
			connectionFailure.addSuppressed(closeFailure);
		}
	}

}
