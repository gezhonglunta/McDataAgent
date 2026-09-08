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

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * 将 MDC 中的 traceId 暴露给 Micrometer Context Propagation，使
 * {@code ContextSnapshot.captureAll() / setThreadLocals()} 能在跨线程手动传播时恢复该值。
 *
 * @author vlsmb
 * @since 2026/9/8
 */
public class TraceIdThreadLocalAccessor implements ThreadLocalAccessor<String> {

	@Override
	public Object key() {
		return TraceIdMdcUtil.TRACE_ID;
	}

	@Override
	public String getValue() {
		return MDC.get(TraceIdMdcUtil.TRACE_ID);
	}

	@Override
	public void setValue(String value) {
		TraceIdMdcUtil.put(value);
	}

	@Override
	public void restore(String previousValue) {
		if (StringUtils.hasText(previousValue)) {
			TraceIdMdcUtil.put(previousValue);
		}
		else {
			TraceIdMdcUtil.clear();
		}
	}

}
