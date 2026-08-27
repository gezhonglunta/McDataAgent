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
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 会话日志追踪 MDC 工具。traceId 使用 conversationId，串联单个会话内的全部日志；
 * runId 使用 threadId，区分会话内每一次图运行。
 *
 * <p>由于 WebFlux 请求与图执行发生在不同线程，跨线程边界时需用 {@link #capture()} /
 * {@link #restore(Map)} 或 {@link #wrap(Runnable)} 手动传播 MDC。
 *
 * @author vlsmb
 * @since 2026/8/26
 */
public final class TraceIdMdcUtil {

	public static final String TRACE_ID = "traceId";

	public static final String RUN_ID = "runId";

	private TraceIdMdcUtil() {
	}

	/** 将 traceId（conversationId）与 runId（threadId）放入当前线程 MDC。 */
	public static void put(String traceId, String runId) {
		if (StringUtils.hasText(traceId)) {
			MDC.put(TRACE_ID, traceId);
		}
		if (StringUtils.hasText(runId)) {
			MDC.put(RUN_ID, runId);
		}
	}

	/** 移除当前线程 MDC 中的 traceId 与 runId。 */
	public static void clear() {
		MDC.remove(TRACE_ID);
		MDC.remove(RUN_ID);
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

	/**
	 * 在指定 traceId/runId 上下文内执行动作，执行完毕后恢复动作之前的 MDC。
	 * 用于无法改动线程模型的短回调（如 SSE 取消/出错回调等）。
	 */
	public static void runWithMdc(String traceId, String runId, Runnable action) {
		callWithMdc(traceId, runId, () -> {
			action.run();
			return null;
		});
	}

	/** 在指定 traceId/runId 上下文内执行并返回结果，结束后恢复之前的 MDC。 */
	public static <T> T callWithMdc(String traceId, String runId, Supplier<T> action) {
		Map<String, String> previous = capture();
		put(traceId, runId);
		try {
			return action.get();
		}
		finally {
			restore(previous);
		}
	}

}
