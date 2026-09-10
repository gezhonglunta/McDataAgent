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
package com.alibaba.cloud.ai.dataagent.service.llm.impls;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 阻塞式（非流式）LLM 实现：所有调用走同步 call()，一次性返回完整结果，
 * 不向模型端点发起 SSE 流式请求。返回类型仍为 Flux 以兼容 LlmService 接口与下游节点，
 * 实际只发射一个元素；用 boundedElastic 调度避免阻塞 Graph 的响应式线程。
 */
@AllArgsConstructor
public class BlockLlmService implements LlmService {

	private final AiModelRegistry registry;

	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return Mono
			.fromCallable(
					() -> registry.getChatClient().prompt().system(system).user(user).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux();
	}

	@Override
	public Flux<ChatResponse> call(String system, String user, Class<?> outputType) {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Mono
			.fromCallable(() -> registry.getChatClient()
				.prompt()
				.system(system)
				.user(user)
				.advisors(advisor)
				.call()
				.chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux();
	}

	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return Mono
			.fromCallable(() -> registry.getChatClient().prompt().system(system).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux();
	}

	@Override
	public Flux<ChatResponse> callUser(String user) {
		return Mono
			.fromCallable(() -> registry.getChatClient().prompt().user(user).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux();
	}

	@Override
	public Flux<ChatResponse> callUser(String user, Class<?> outputType) {
		StructuredOutputValidationAdvisor advisor = StructuredOutputValidationAdvisor.builder()
			.outputType(outputType)
			.maxRepeatAttempts(2)
			.build();
		return Mono
			.fromCallable(() -> registry.getChatClient().prompt().user(user).advisors(advisor).call().chatResponse())
			.subscribeOn(Schedulers.boundedElastic())
			.flux();
	}

}
