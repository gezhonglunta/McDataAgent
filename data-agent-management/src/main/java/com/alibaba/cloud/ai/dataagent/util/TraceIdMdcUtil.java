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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshot.Scope;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 日志追踪 MDC 工具。traceId 默认取自用户请求（userId），由 {@code TraceIdWebFilter}
 * 在请求入口统一设置，跨线程边界通过 {@link ContextSnapshot}（{@code captureSnapshot()} /
 * {@code restoreSnapshot(...)}）、{@code MdcPropagatingScheduledExecutorService} 或
 * {@link #wrap(Runnable)} 手动传播。
 *
 * @author vlsmb
 * @since 2026/8/26
 */
public final class TraceIdMdcUtil {

	public static final String TRACE_ID = "traceId";

	private TraceIdMdcUtil() {
	}

	/** 将 traceId 放入当前线程 MDC。 */
	public static void put(String traceId) {
		if (StringUtils.hasText(traceId)) {
			MDC.put(TRACE_ID, traceId);
		}
	}

	/** 移除当前线程 MDC 中的 traceId。 */
	public static void clear() {
		MDC.remove(TRACE_ID);
	}

	/** userId 有值时用作 traceId，否则生成随机 UUID。 */
	public static String resolveTraceId(String userId) {
		return StringUtils.hasText(userId) ? userId : UUID.randomUUID().toString();
	}

	/** 捕获当前线程的完整 MDC 上下文副本，用于跨线程传播。 */
	public static Map<String, String> capture() {
		Map<String, String> contextMap = MDC.getCopyOfContextMap();
		return contextMap == null ? Map.of() : contextMap;
	}

	/** 用捕获的上下文恢复（覆盖）当前线程的 MDC。 */
	public static void restore(Map<String, String> contextMap) {
		MDC.clear();
		if (contextMap != null && !contextMap.isEmpty()) {
			MDC.setContextMap(contextMap);
		}
	}

	/**
	 * 捕获当前线程已注册 ThreadLocal（traceId accessor）的快照，供跨线程手动传播。
	 * 需在 {@code TraceIdThreadLocalAccessor} 注册到 {@code ContextRegistry} 后生效。
	 */
	public static ContextSnapshot captureSnapshot() {
		return ContextSnapshot.captureAll();
	}

	/** 将快照恢复到当前线程，返回的 {@code Scope} 需在使用完毕后调用 {@code close()}。 */
	public static Scope restoreSnapshot(ContextSnapshot snapshot) {
		return snapshot.setThreadLocals();
	}

	/** 包装任务：执行前恢复调用线程的 MDC，结束后清空，避免线程池复用导致串号。 */
	public static Runnable wrap(Runnable runnable) {
		Map<String, String> contextMap = capture();
		return () -> {
			restore(contextMap);
			try {
				runnable.run();
			}
			finally {
				MDC.clear();
			}
		};
	}

	/** 包装带返回值的任务：执行前恢复调用线程的 MDC，结束后清空。 */
	public static <T> Callable<T> wrapCallable(Callable<T> callable) {
		Map<String, String> contextMap = capture();
		return () -> {
			restore(contextMap);
			try {
				return callable.call();
			}
			finally {
				MDC.clear();
			}
		};
	}

	/**
	 * 在指定 traceId 上下文内执行动作，执行完毕后恢复动作之前的 MDC。
	 * 用于无法改动线程模型的短回调（如 SSE 取消/出错回调等）。
	 */
	public static void runWithMdc(String traceId, Runnable action) {
		callWithMdc(traceId, () -> {
			action.run();
			return null;
		});
	}

	/** 在指定 traceId 上下文内执行并返回结果，结束后恢复之前的 MDC。 */
	public static <T> T callWithMdc(String traceId, Supplier<T> action) {
		Map<String, String> previous = capture();
		put(traceId);
		try {
			return action.get();
		}
		finally {
			restore(previous);
		}
	}

}
