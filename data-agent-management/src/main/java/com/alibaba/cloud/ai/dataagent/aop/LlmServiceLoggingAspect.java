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

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP切面类，用于记录LLM调用入参日志
 */
@Aspect
@Component
@Slf4j
public class LlmServiceLoggingAspect {
	private static final Logger loggerLlm = LoggerFactory.getLogger("LOG_LLM");

	@Pointcut("execution(* com.alibaba.cloud.ai.dataagent.service.llm.LlmService.call(..)) "
			+ "|| execution(* com.alibaba.cloud.ai.dataagent.service.llm.LlmService.callSystem(..)) "
			+ "|| execution(* com.alibaba.cloud.ai.dataagent.service.llm.LlmService.callUser(..))")
	public void llmServiceMethods() {
	}

	@Pointcut("target(com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService) ")
	public void llmServiceImpls() {
	}

	@Before("llmServiceMethods() && llmServiceImpls()")
	public void logLlmServiceArgs(JoinPoint joinPoint) {
		String className = joinPoint.getTarget().getClass().getSimpleName();
		String methodName = joinPoint.getSignature().getName();
		Object[] args = joinPoint.getArgs();
		String system = "";
		String user = "";

		if ("call".equals(methodName)) {
			system = getArgument(args, 0);
			user = getArgument(args, 1);
		} else if ("callSystem".equals(methodName)) {
			system = getArgument(args, 0);
		} else if ("callUser".equals(methodName)) {
			user = getArgument(args, 0);
		}
		String logId = UUID.randomUUID().toString().replace("-", "");
		loggerLlm.info("[{}]{}.{}:\nsystem prompts:\n{}\nuser prompts:\n{}", logId, className, methodName, system, user);
		log.info("[{}]call llm : {}.{}", logId, className, methodName);
	}

	private String getArgument(Object[] args, int index) {
		if (args == null || args.length <= index || args[index] == null) {
			return "";
		}
		return args[index].toString();
	}

}
