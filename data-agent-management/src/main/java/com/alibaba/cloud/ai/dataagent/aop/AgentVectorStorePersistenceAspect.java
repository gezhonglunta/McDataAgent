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
package com.alibaba.cloud.ai.dataagent.aop;

import com.alibaba.cloud.ai.dataagent.service.ApplicationContextHelper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.SimpleVectorStoreInitialization;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class AgentVectorStorePersistenceAspect {

	private static final long SAVE_DELAY_MINUTES = 5L;

	private final VectorStore vectorStore;

	private final ScheduledExecutorService scheduler;

	private final Object saveTaskLock = new Object();

	private ScheduledFuture<?> pendingSaveTask;

	public AgentVectorStorePersistenceAspect(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread thread = new Thread(r, "simple-vector-store-save-scheduler");
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	@AfterReturning("execution(* com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService.addDocuments(..))")
	public void scheduleSaveAfterAddDocuments() {
		if (!(vectorStore instanceof SimpleVectorStore)) {
			return;
		}

		synchronized (saveTaskLock) {
			if (pendingSaveTask != null) {
				pendingSaveTask.cancel(false);
			}

			pendingSaveTask = scheduler.schedule(this::saveSimpleVectorStoreSafely, SAVE_DELAY_MINUTES,
					TimeUnit.MINUTES);
		}
	}

	private void saveSimpleVectorStoreSafely() {
		synchronized (saveTaskLock) {
			pendingSaveTask = null;
		}

		SimpleVectorStoreInitialization initialization = ApplicationContextHelper
			.getBean(SimpleVectorStoreInitialization.class);
		if (initialization == null) {
			log.debug("Skip saving simple vector store because SimpleVectorStoreInitialization is unavailable.");
			return;
		}

		try {
			initialization.save();
		}
		catch (Exception e) {
			log.error("Failed to persist simple vector store after addDocuments debounce.", e);
		}
	}

	@PreDestroy
	public void shutdownScheduler() {
		scheduler.shutdownNow();
	}

}
