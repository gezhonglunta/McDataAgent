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
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Ensures the configured Docker image is locally available.
 */
@Component
public class DockerImageManager {

	private final Supplier<PullImageResultCallback> callbackFactory;

	public DockerImageManager() {
		this(PullImageResultCallback::new);
	}

	DockerImageManager(Supplier<PullImageResultCallback> callbackFactory) {
		this.callbackFactory = Objects.requireNonNull(callbackFactory, "callbackFactory");
	}

	public void ensureAvailable(DockerClient client, String imageName) {
		List<Image> images = client.listImagesCmd().withImageNameFilter(imageName).exec();
		boolean imageExists = images != null && images.stream()
			.map(Image::getRepoTags)
			.filter(Objects::nonNull)
			.flatMap(Arrays::stream)
			.anyMatch(imageName::equals);
		if (imageExists) {
			return;
		}

		PullImageResultCallback callback = callbackFactory.get();
		try {
			client.pullImageCmd(imageName).exec(callback).awaitCompletion();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while pulling Docker image " + imageName, exception);
		}
	}

}
