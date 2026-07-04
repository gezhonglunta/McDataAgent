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
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.github.dockerjava.api.model.HostConfig.newHostConfig;

/**
 * 运行Python任务的容器池（Docker实现类）
 *
 * @author vlsmb
 * @since 2025/7/12
 */
@Slf4j
public class DockerCodePoolExecutorService extends AbstractCodePoolExecutorService implements CodePoolExecutorService {

	private final DockerClient dockerClient;

	private final boolean isRemote;

	private final ConcurrentHashMap<String, Path> containerTempPath;

	public DockerCodePoolExecutorService(CodeExecutorProperties properties, DockerClient dockerClient,
			boolean isRemote) {
		super(properties);
		this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient");
		this.isRemote = isRemote;
		this.containerTempPath = new ConcurrentHashMap<>();
		log.info("Docker Code Pool initialized. Mode: {}",
				this.isRemote ? "Remote (Copy Files)" : "Local (Bind Mounts)");
	}

	/**
	 * Create container's HostConfig
	 */
	private HostConfig createHostConfig(Path tempDir) {
		HostConfig config = newHostConfig().withMemory(this.properties.getLimitMemory() * 1024L * 1024L)
			.withCpuCount(this.properties.getCpuCore())
			.withCapDrop(Capability.ALL)
			.withAutoRemove(false)
			.withTmpFs(Map.of("/tmp", ""))
			.withNetworkMode(this.properties.getNetworkMode());

		if (!this.isRemote) {
			List<Bind> binds = new ArrayList<>();
			binds.add(new Bind(tempDir.resolve("script.py").toAbsolutePath().toString(), new Volume("/app/script.py"),
					AccessMode.ro));
			binds.add(new Bind(tempDir.resolve("requirements.txt").toAbsolutePath().toString(),
					new Volume("/app/requirements.txt"), AccessMode.ro));
			binds.add(new Bind(tempDir.resolve("input_data.txt").toAbsolutePath().toString(),
					new Volume("/app/input_data.txt"), AccessMode.ro));
			config.withBinds(binds.toArray(new Bind[0]));
		}
		return config;
	}

	/**
	 * Clean up existing container with same name
	 */
	private void cleanupExistingResources(String containName) {
		try {
			// Try to delete container with same name
			dockerClient.removeContainerCmd(containName).withForce(true).exec();
			log.info("Removed existing container: {}", containName);
		}
		catch (Exception e) {
			log.warn("Failed to remove container {}: {}", containName, e.getMessage());
		}
	}

	@Override
	protected String createNewContainer() throws Exception {
		String containerName = this.generateContainerName();
		// First clean up possibly existing container with same name
		this.cleanupExistingResources(containerName);

		// Generate temporary directory and files
		Path tempDir = Files.createTempDirectory(containerName);
		Files.createFile(tempDir.resolve("requirements.txt"));
		Files.createFile(tempDir.resolve("script.py"));
		Files.createFile(tempDir.resolve("input_data.txt"));

		// Create container
		HostConfig hostConfig = this.createHostConfig(tempDir);
		String cmd = this.buildExecutionCommand(tempDir);

		CreateContainerResponse container = dockerClient.createContainerCmd(properties.getImageName())
			.withName(containerName)
			.withWorkingDir("/app")
			.withHostConfig(hostConfig)
			.withCmd("sh", "-c", cmd)
			.exec();
		String containerId = container.getId();
		// Save temporary directory object
		this.containerTempPath.put(containerId, tempDir);
		return containerId;
	}

	@Override
	protected TaskResponse execTaskInContainer(TaskRequest request, String containerId) {
		// Get temporary directory object
		Path tempDir = this.containerTempPath.get(containerId);
		if (tempDir == null) {
			log.error("Container '{}' does not exist work dir", containerId);
			return TaskResponse.exception("Container '" + containerId + "' does not exist work dir");
		}

		try {
			// 1. Prepare files
			this.writeContextFiles(tempDir, request);
			this.uploadFilesIfRemote(containerId, tempDir);

			// 2. Start container and wait
			dockerClient.startContainerCmd(containerId).exec();
			dockerClient.waitContainerCmd(containerId)
				.start()
				.awaitCompletion(this.properties.getContainerTimeout(), TimeUnit.SECONDS);

			// 3. Fetch logs
			LogResult logs = this.fetchExecutionLogs(containerId, tempDir);
			String stdout = logs.stdout;
			String stderr = logs.stderr;

			// 4. Check exit code
			InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
			int exitCode = Objects.requireNonNull(inspectResponse.getState().getExitCodeLong()).intValue();
			if (exitCode != 0) {
				String errorMessage = "Docker exit code " + exitCode + ". Stderr: " + stderr + ". Stdout: " + stdout;
				log.error("Error executing Docker container {}: {}", containerId, errorMessage);
				return TaskResponse.failure(stdout, stderr);
			}
			return TaskResponse.success(stdout);
		}
		catch (Exception e) {
			log.error("Error executing task in container: {}", e.getMessage());
			return TaskResponse.exception(e.getMessage());
		}
	}

	// --- Helper Methods ---

	private String buildExecutionCommand(Path tempDir) {
		return String.format(
				"if [ -s requirements.txt ]; then pip3 install --no-cache-dir -r requirements.txt > /dev/null; fi && timeout -s SIGKILL %s python3 -u script.py < input_data.txt",
				properties.getCodeTimeout());
	}

	private void writeContextFiles(Path tempDir, TaskRequest request) throws IOException {
		Files.write(tempDir.resolve("script.py"),
				StringUtils.hasText(request.code()) ? request.code().getBytes() : "".getBytes());
		Files.write(tempDir.resolve("requirements.txt"),
				StringUtils.hasText(request.requirement()) ? request.requirement().getBytes() : "".getBytes());
		Files.write(tempDir.resolve("input_data.txt"),
				StringUtils.hasText(request.input()) ? request.input().getBytes() : "".getBytes());
	}

	private void uploadFilesIfRemote(String containerId, Path tempDir) {
		if (!this.isRemote) {
			return;
		}
		String[] files = { "script.py", "requirements.txt", "input_data.txt" };
		for (String file : files) {
			dockerClient.copyArchiveToContainerCmd(containerId)
				.withHostResource(tempDir.resolve(file).toString())
				.withRemotePath("/app/")
				.exec();
		}
	}

	private record LogResult(String stdout, String stderr) {
	}

	private LogResult fetchExecutionLogs(String containerId, Path tempDir) throws InterruptedException {
		StringBuilder stdoutBuilder = new StringBuilder();
		StringBuilder stderrBuilder = new StringBuilder();

		final int MAX_LOG_SIZE = 5 * 1024 * 1024; // 5MB limit
		dockerClient.logContainerCmd(containerId)
			.withStdOut(true)
			.withStdErr(true)
			.exec(new ResultCallback.Adapter<Frame>() {
				@Override
				public void onNext(Frame item) {
					String payload = new String(item.getPayload(), StandardCharsets.UTF_8);
					if (item.getStreamType() == StreamType.STDOUT) {
						appendWithLimit(stdoutBuilder, payload, MAX_LOG_SIZE);
					}
					else if (item.getStreamType() == StreamType.STDERR) {
						appendWithLimit(stderrBuilder, payload, MAX_LOG_SIZE);
					}
				}
			})
			.awaitCompletion();

		return new LogResult(stdoutBuilder.toString(), stderrBuilder.toString());
	}

	private void appendWithLimit(StringBuilder builder, String payload, int limit) {
		if (builder.length() < limit) {
			builder.append(payload);
		}
		else if (builder.length() == limit) {
			builder.append("\n...[Output truncated due to size limit]...");
			builder.append(" "); // Prevent re-entry
		}
	}

	@Override
	protected void stopContainer(String containerId) throws Exception {
		try {
			this.dockerClient.stopContainerCmd(containerId).exec();
			log.info("Successfully stopped container: {}", containerId);
		}
		catch (Exception e) {
			log.warn("Failed to stop container: {}, message: {}", containerId, e.getMessage());
		}
	}

	@Override
	protected void removeContainer(String containerId) throws Exception {
		try {
			this.dockerClient.removeContainerCmd(containerId).withForce(true).exec();
			Path tempDir = containerTempPath.get(containerId);
			if (tempDir != null) {
				this.clearTempDir(tempDir);
			}
			containerTempPath.remove(containerId);
			log.info("Successfully removed container: {}", containerId);
		}
		catch (Exception e) {
			log.warn("Failed to remove container: {}, message: {}", containerId, e.getMessage());
		}
	}

	@Override
	protected void shutdownPool() throws Exception {
		try {
			super.shutdownPool();
			this.dockerClient.close();
		}
		catch (IOException ignored) {

		}
	}

}
