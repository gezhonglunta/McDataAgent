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
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 包装 {@link ScheduledExecutorService}，在任务提交时捕获调用线程的 MDC，任务执行时恢复，
 * 使 Reactor Scheduler 上的异步节点日志自动携带会话 traceId/runId。
 *
 * @author vlsmb
 * @since 2026/8/26
 */
public class MdcPropagatingScheduledExecutorService implements ScheduledExecutorService {

	private final ScheduledExecutorService delegate;

	public MdcPropagatingScheduledExecutorService(ScheduledExecutorService delegate) {
		this.delegate = delegate;
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return delegate.schedule(TraceIdMdcUtil.wrap(command), delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		return delegate.schedule(wrap(callable), delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		return delegate.scheduleAtFixedRate(TraceIdMdcUtil.wrap(command), initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return delegate.scheduleWithFixedDelay(TraceIdMdcUtil.wrap(command), initialDelay, delay, unit);
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

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return delegate.submit(wrap(task));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return delegate.submit(TraceIdMdcUtil.wrap(task), result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return delegate.submit(TraceIdMdcUtil.wrap(task));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return delegate.invokeAll(tasks.stream().map(this::wrap).toList());
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		return delegate.invokeAll(tasks.stream().map(this::wrap).toList(), timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		return delegate.invokeAny(tasks.stream().map(this::wrap).toList());
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return delegate.invokeAny(tasks.stream().map(this::wrap).toList(), timeout, unit);
	}

	@Override
	public void execute(Runnable command) {
		delegate.execute(TraceIdMdcUtil.wrap(command));
	}

	private <T> Callable<T> wrap(Callable<T> callable) {
		Map<String, String> contextMap = TraceIdMdcUtil.capture();
		return () -> {
			TraceIdMdcUtil.restore(contextMap);
			try {
				return callable.call();
			}
			finally {
				TraceIdMdcUtil.clear();
			}
		};
	}

}
