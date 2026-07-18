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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.dto.GraphRequest;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.dataagent.vo.GraphNodeResponse;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceImplTest {

	@Mock
	private CompiledGraph compiledGraph;

	@Mock
	private MultiTurnContextManager multiTurnContextManager;

	@Mock
	private LangfuseService langfuseReporter;

	@Mock
	private BaseCheckpointSaver checkpointSaver;

	@Mock
	private Span mockSpan;

	private GraphServiceImpl graphService;

	private ExecutorService executor;

	@BeforeEach
	void setUp() throws Exception {
		executor = Executors.newSingleThreadExecutor();

		StateGraph mockStateGraph = mock(StateGraph.class);
		when(mockStateGraph.compile(any())).thenReturn(compiledGraph);

		CompileConfig compileConfig = CompileConfig.builder().build();
		graphService = new GraphServiceImpl(mockStateGraph, compileConfig, checkpointSaver, executor,
				multiTurnContextManager, langfuseReporter);

		when(langfuseReporter.startLLMSpan(anyString(), any())).thenReturn(mockSpan);
		when(mockSpan.isRecording()).thenReturn(true);
		when(multiTurnContextManager.buildContext(anyString())).thenReturn("(无)");
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	void nl2sql_validQuery_returnsResult() throws GraphRunnerException {
		OverAllState mockState = mock(OverAllState.class);
		when(mockState.value(eq("SQL_GENERATE_OUTPUT"), eq(""))).thenReturn("SELECT * FROM users");
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.of(mockState));

		String result = graphService.nl2sql("show all users", "1");

		assertEquals("SELECT * FROM users", result);
		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		verify(compiledGraph).invoke(anyMap(), configCaptor.capture());
		assertTrue(configCaptor.getValue().threadId().isPresent());
		assertNotEquals(BaseCheckpointSaver.THREAD_ID_DEFAULT, configCaptor.getValue().threadId().orElseThrow());
		try {
			verify(checkpointSaver).release(configCaptor.getValue());
		}
		catch (Exception e) {
			fail(e);
		}
	}

	@Test
	void nl2sql_emptyResult_returnsEmptyString() throws GraphRunnerException {
		OverAllState mockState = mock(OverAllState.class);
		when(mockState.value(eq("SQL_GENERATE_OUTPUT"), eq(""))).thenReturn("");
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.of(mockState));

		String result = graphService.nl2sql("invalid query", "1");

		assertEquals("", result);
	}

	@Test
	void graphStreamProcess_newProcess_setsThreadIdIfMissing() {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();

		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(sink, request);

		assertNotNull(request.getThreadId());
		assertFalse(request.getThreadId().isEmpty());
		assertNotEquals(request.getConversationId(), request.getThreadId());
	}

	@Test
	void graphStreamProcess_legacyThreadId_startsFreshRunAndKeepsConversationIdentity() {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.threadId("existing-thread")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();

		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(sink, request);

		assertEquals("existing-thread", request.getConversationId());
		assertNotEquals("existing-thread", request.getThreadId());
	}

	@Test
	void graphStreamProcess_humanFeedback_reusesInterruptedRunId() throws Exception {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-1")
			.threadId("interrupted-run")
			.query("test query")
			.humanFeedback(true)
			.humanFeedbackContent("approve")
			.build();
		RunnableConfig updatedConfig = RunnableConfig.builder().threadId("interrupted-run").build();
		when(compiledGraph.updateState(any(RunnableConfig.class), anyMap())).thenReturn(updatedConfig);
		when(compiledGraph.stream(isNull(), any(RunnableConfig.class))).thenReturn(Flux.empty());

		graphService.graphStreamProcess(Sinks.many().multicast().onBackpressureBuffer(), request);

		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		verify(compiledGraph).updateState(configCaptor.capture(), anyMap());
		assertEquals("interrupted-run", configCaptor.getValue().threadId().orElseThrow());
		assertEquals("interrupted-run", request.getThreadId());
	}

	@Test
	void stopStreamProcessing_nullThreadId_doesNothing() {
		assertDoesNotThrow(() -> graphService.stopStreamProcessing(null));
		assertDoesNotThrow(() -> graphService.stopStreamProcessing(""));
	}

	@Test
	void stopStreamProcessing_unknownThread_doesNothing() {
		assertDoesNotThrow(() -> graphService.stopStreamProcessing("unknown-thread"));
		verify(multiTurnContextManager).discardPending("unknown-thread");
	}

	@Test
	void stopStreamProcessing_existingThread_cleansUp() {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-to-stop")
			.threadId("thread-to-stop")
			.query("test query")
			.build();

		Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().multicast().onBackpressureBuffer();
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class))).thenReturn(Flux.never());

		graphService.graphStreamProcess(sink, request);
		String runId = request.getThreadId();

		try {
			Thread.sleep(100);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		graphService.stopStreamProcessing(runId);
		verify(multiTurnContextManager).discardPending("conversation-to-stop");
	}

	@Test
	void stopStreamProcessingByConversationId_cancelsActiveGraphSubscription() throws Exception {
		GraphRequest request = GraphRequest.builder()
			.agentId("1")
			.conversationId("conversation-to-cancel")
			.query("test query")
			.build();
		CountDownLatch subscribed = new CountDownLatch(1);
		CountDownLatch cancelled = new CountDownLatch(1);
		when(compiledGraph.stream(anyMap(), any(RunnableConfig.class)))
			.thenReturn(Flux.<com.alibaba.cloud.ai.graph.NodeOutput>never()
				.doOnSubscribe(ignored -> subscribed.countDown())
				.doOnCancel(cancelled::countDown));

		graphService.graphStreamProcess(Sinks.many().multicast().onBackpressureBuffer(), request);

		assertTrue(subscribed.await(2, TimeUnit.SECONDS));
		graphService.stopStreamProcessingByConversationId("conversation-to-cancel");

		assertTrue(cancelled.await(2, TimeUnit.SECONDS));
		verify(multiTurnContextManager).discardPending("conversation-to-cancel");
		var configCaptor = org.mockito.ArgumentCaptor.forClass(RunnableConfig.class);
		try {
			verify(checkpointSaver).release(configCaptor.capture());
		}
		catch (Exception e) {
			fail(e);
		}
		assertEquals(request.getThreadId(), configCaptor.getValue().threadId().orElseThrow());
	}

	@Test
	void nl2sql_graphRunnerException_throwsException() {
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class)))
			.thenThrow(new RuntimeException("Graph execution failed"));

		assertThrows(RuntimeException.class, () -> graphService.nl2sql("test", "1"));
	}

	@Test
	void nl2sql_emptyOptional_returnsEmpty() throws GraphRunnerException {
		when(compiledGraph.invoke(anyMap(), any(RunnableConfig.class))).thenReturn(Optional.empty());

		assertThrows(Exception.class, () -> graphService.nl2sql("test", "1"));
	}

}
