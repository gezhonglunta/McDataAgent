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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import java.util.Objects;

/**
 * Retry limits and backoff behavior for database connection acquisition.
 */
public final class ConnectionRetryPolicy {

	private static final long BASE_DELAY_MILLIS = 1_000L;

	private final int maxAttempts;

	private final Sleeper sleeper;

	public ConnectionRetryPolicy(int maxAttempts, Sleeper sleeper) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1");
		}
		this.maxAttempts = maxAttempts;
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
	}

	public static ConnectionRetryPolicy defaults() {
		return new ConnectionRetryPolicy(3, Thread::sleep);
	}

	public int maxAttempts() {
		return maxAttempts;
	}

	public void pauseAfterFailure(int failedAttempt) throws InterruptedException {
		sleeper.sleep(Math.multiplyExact(BASE_DELAY_MILLIS, failedAttempt));
	}

	@FunctionalInterface
	public interface Sleeper {

		void sleep(long millis) throws InterruptedException;

	}

}
