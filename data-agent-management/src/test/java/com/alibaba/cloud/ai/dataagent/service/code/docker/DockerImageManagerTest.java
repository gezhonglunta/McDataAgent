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
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerImageManagerTest {

	private static final String IMAGE_NAME = "python:test";

	@Mock
	private DockerClient client;

	@Mock
	private ListImagesCmd listImagesCmd;

	@Mock
	private PullImageCmd pullImageCmd;

	@Mock
	private PullImageResultCallback pullCallback;

	@Mock
	private Image image;

	@Mock
	private Supplier<PullImageResultCallback> callbackFactory;

	private DockerImageManager imageManager;

	@BeforeEach
	void setUp() {
		imageManager = new DockerImageManager(callbackFactory);
		when(client.listImagesCmd()).thenReturn(listImagesCmd);
		when(listImagesCmd.withImageNameFilter(IMAGE_NAME)).thenReturn(listImagesCmd);
	}

	@AfterEach
	void clearInterruptedFlag() {
		Thread.interrupted();
	}

	@Test
	void ensureAvailable_existingExactTag_doesNotPull() {
		when(listImagesCmd.exec()).thenReturn(List.of(image));
		when(image.getRepoTags()).thenReturn(new String[] { "other:test", IMAGE_NAME });

		imageManager.ensureAvailable(client, IMAGE_NAME);

		verify(client, never()).pullImageCmd(anyString());
	}

	@Test
	void ensureAvailable_missingTag_pullsAndWaitsForCompletion() throws InterruptedException {
		when(listImagesCmd.exec()).thenReturn(List.of(image));
		when(image.getRepoTags()).thenReturn(new String[] { "other:test" });
		stubPull();

		imageManager.ensureAvailable(client, IMAGE_NAME);

		verify(client).pullImageCmd(IMAGE_NAME);
		verify(pullCallback).awaitCompletion();
	}

	@Test
	void ensureAvailable_nullTags_areTreatedAsMissing() throws InterruptedException {
		when(listImagesCmd.exec()).thenReturn(List.of(image));
		when(image.getRepoTags()).thenReturn(null);
		stubPull();

		imageManager.ensureAvailable(client, IMAGE_NAME);

		verify(pullCallback).awaitCompletion();
	}

	@Test
	void ensureAvailable_interruptedPull_restoresInterruptAndThrowsSpecificFailure() throws InterruptedException {
		when(listImagesCmd.exec()).thenReturn(List.of());
		stubPull();
		when(pullCallback.awaitCompletion()).thenThrow(new InterruptedException("cancelled"));

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> imageManager.ensureAvailable(client, IMAGE_NAME));

		assertTrue(error.getMessage().contains(IMAGE_NAME));
		assertTrue(Thread.currentThread().isInterrupted());
	}

	private void stubPull() {
		when(callbackFactory.get()).thenReturn(pullCallback);
		when(client.pullImageCmd(IMAGE_NAME)).thenReturn(pullImageCmd);
		when(pullImageCmd.exec(pullCallback)).thenReturn(pullCallback);
	}

}
