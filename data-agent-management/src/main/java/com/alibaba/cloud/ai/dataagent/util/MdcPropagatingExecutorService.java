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
package com.alibaba.cloud.ai.dataagent.util;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshot.Scope;

/**
 * 包装普通 {@link ExecutorService}，任务提交时通过 {@link ContextSnapshot} 捕获调用线程的
 * traceId，任务执行时恢复并透传，确保独立线程池（如数据库操作线程池）内的日志可被追踪。
 *
 * @author vlsmb
 * @since 2026/9/8
 */
public class MdcPropagatingExecutorService implements ExecutorService {

	private final ExecutorService delegate;

	public MdcPropagatingExecutorService(ExecutorService delegate) {
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable command) {
		delegate.execute(snapshotWrap(command));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return delegate.submit(snapshotWrap(task));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return delegate.submit(snapshotWrap(task), result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return delegate.submit(snapshotWrap(task));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return delegate.invokeAll(tasks.stream().map(this::snapshotWrap).toList());
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		return delegate.invokeAll(tasks.stream().map(this::snapshotWrap).toList(), timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return delegate.invokeAny(tasks.stream().map(this::snapshotWrap).toList());
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return delegate.invokeAny(tasks.stream().map(this::snapshotWrap).toList(), timeout, unit);
	}

	@Override
	public void shutdown() {
		delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return delegate.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return delegate.awaitTermination(timeout, unit);
	}

	private Runnable snapshotWrap(Runnable runnable) {
		ContextSnapshot snapshot = TraceIdMdcUtil.captureSnapshot();
		return () -> {
			try (Scope ignored = TraceIdMdcUtil.restoreSnapshot(snapshot)) {
				runnable.run();
			}
			finally {
				TraceIdMdcUtil.clear();
			}
		};
	}

	private <T> Callable<T> snapshotWrap(Callable<T> callable) {
		ContextSnapshot snapshot = TraceIdMdcUtil.captureSnapshot();
		return () -> {
			try (Scope ignored = TraceIdMdcUtil.restoreSnapshot(snapshot)) {
				return callable.call();
			}
			finally {
				TraceIdMdcUtil.clear();
			}
		};
	}

}
