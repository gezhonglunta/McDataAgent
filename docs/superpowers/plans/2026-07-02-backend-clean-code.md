# Backend Clean Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox
> (`- [ ]`) syntax for tracking.

**Goal:** Make the default backend tests deterministic and trustworthy, then use those tests
to refactor Docker infrastructure boundaries and correct DTO builder-default behavior.

**Architecture:** Move operating-system policy, Docker connection, and image preparation out
of `DockerCodePoolExecutorService`. Construct the executor from explicit dependencies at the
composition root. Unit tests exercise pure policies and injected Docker boundaries; a named
integration test retains the real-daemon check outside the default suite.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Maven Surefire, docker-java, Lombok

---

### Task 1: Establish deterministic Docker host policy

**Files:**
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerHostResolverTest.java`
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerHostResolver.java`

- [ ] **Step 1: Write the failing positive, negative, and boundary tests**

```java
class DockerHostResolverTest {

	private final DockerHostResolver resolver = new DockerHostResolver();

	@Test
	void candidates_explicitHost_keepsConfiguredHostFirst() {
		assertEquals(List.of("tcp://docker.example:2375"),
				resolver.candidates("tcp://docker.example:2375", "Linux"));
	}

	@Test
	void candidates_explicitWindowsHost_keepsFallbacksWithoutDuplicates() {
		assertEquals(
				List.of("tcp://docker.example:2375", "npipe://./pipe/docker_engine",
						"tcp://localhost:2375"),
				resolver.candidates("tcp://docker.example:2375", "Windows 11"));
	}

	@Test
	void candidates_windowsWithoutHost_returnsNamedPipeThenLocalTcp() {
		assertEquals(List.of("npipe://./pipe/docker_engine", "tcp://localhost:2375"),
				resolver.candidates(null, "Windows 11"));
	}

	@Test
	void candidates_unixWithoutHost_returnsUnixSocket() {
		assertEquals(List.of("unix:///var/run/docker.sock"), resolver.candidates(null, "Mac OS X"));
	}

	@Test
	void isRemote_distinguishesLocalAndRemoteTcpHosts() {
		assertFalse(resolver.isRemote("tcp://localhost:2375"));
		assertFalse(resolver.isRemote("tcp://127.0.0.1:2375"));
		assertFalse(resolver.isRemote("tcp://[::1]:2375"));
		assertTrue(resolver.isRemote("tcp://docker.example:2375"));
	}

	@Test
	void isRemote_malformedHost_isRejectedInsteadOfSilentlyClassified() {
		assertThrows(IllegalArgumentException.class, () -> resolver.isRemote("not a docker uri"));
	}
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./mvnw -pl data-agent-management -Dtest=DockerHostResolverTest test
```

Expected: compilation fails because `DockerHostResolver` does not exist.

- [ ] **Step 3: Implement the pure policy**

```java
public final class DockerHostResolver {

	private static final String UNIX_SOCKET = "unix:///var/run/docker.sock";

	public List<String> candidates(String configuredHost, String osName) {
		String normalizedOs = Objects.requireNonNullElse(osName, "").toLowerCase(Locale.ROOT);
		if (normalizedOs.contains("win")) {
			LinkedHashSet<String> hosts = new LinkedHashSet<>();
			if (StringUtils.hasText(configuredHost)) {
				hosts.add(configuredHost);
			}
			hosts.add("npipe://./pipe/docker_engine");
			hosts.add("tcp://localhost:2375");
			return List.copyOf(hosts);
		}
		if (StringUtils.hasText(configuredHost)) {
			return List.of(configuredHost);
		}
		return List.of(UNIX_SOCKET);
	}

	public boolean isRemote(String host) {
		if (!StringUtils.hasText(host)) {
			return false;
		}
		URI uri;
		try {
			uri = URI.create(host);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid Docker host URI: " + host, exception);
		}
		if ("unix".equalsIgnoreCase(uri.getScheme()) || "npipe".equalsIgnoreCase(uri.getScheme())) {
			return false;
		}
		if (!"tcp".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			throw new IllegalArgumentException("Unsupported Docker host URI: " + host);
		}
		String hostname = uri.getHost();
		return !("localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname)
				|| "::1".equals(hostname));
	}
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: all `DockerHostResolverTest` tests pass without Docker.

- [ ] **Step 5: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerHostResolver.java \
  data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerHostResolverTest.java
git commit -m "refactor: isolate Docker host policy"
```

### Task 2: Isolate Docker connection and image preparation

**Files:**
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerClientConnector.java`
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/ZerodepDockerClientConnector.java`
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerClientFactory.java`
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerImageManager.java`
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerClientFactoryTest.java`
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerImageManagerTest.java`

- [ ] **Step 1: Write failing connection fallback tests**

```java
@ExtendWith(MockitoExtension.class)
class DockerClientFactoryTest {

	@Mock
	private DockerClientConnector connector;

	@Mock
	private DockerClient client;

	@Test
	void create_firstCandidateWorks_returnsClientWithoutTryingFallback() {
		PingCmd ping = mock(PingCmd.class);
		when(connector.connect("npipe://./pipe/docker_engine")).thenReturn(client);
		when(client.pingCmd()).thenReturn(ping);

		DockerClient result = new DockerClientFactory(connector)
			.create(List.of("npipe://./pipe/docker_engine", "tcp://localhost:2375"));

		assertSame(client, result);
		verify(ping).exec();
		verify(connector, never()).connect("tcp://localhost:2375");
	}

	@Test
	void create_firstCandidateFails_usesFallback() {
		when(connector.connect("npipe://./pipe/docker_engine")).thenThrow(new RuntimeException("pipe unavailable"));
		when(connector.connect("tcp://localhost:2375")).thenReturn(client);

		assertSame(client, new DockerClientFactory(connector)
			.create(List.of("npipe://./pipe/docker_engine", "tcp://localhost:2375")));
	}

	@Test
	void create_allCandidatesFail_reportsAttemptedHosts() {
		when(connector.connect(anyString())).thenThrow(new RuntimeException("unavailable"));

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new DockerClientFactory(connector).create(List.of("unix:///one", "unix:///two")));

		assertTrue(error.getMessage().contains("unix:///one"));
		assertTrue(error.getMessage().contains("unix:///two"));
	}
}
```

The concrete test stubs `PingCmd.exec()` so success requires an actual ping call, not merely
client construction.

- [ ] **Step 2: Write failing image comparison tests**

The tests cover both branches:

```java
@Test
void ensureAvailable_existingTag_doesNotPull() {
	// listImages returns the requested tag
	imageManager.ensureAvailable(client, "python:test");
	verify(client, never()).pullImageCmd(anyString());
}

@Test
void ensureAvailable_missingTag_pullsAndWaitsForCompletion() throws Exception {
	// listImages returns no matching tag
	imageManager.ensureAvailable(client, "python:test");
	verify(pullCallback).awaitCompletion();
}

@Test
void ensureAvailable_nullRepoTags_isTreatedAsMissing() {
	// protects against Arrays.asList(null)
	assertDoesNotThrow(() -> imageManager.ensureAvailable(client, "python:test"));
}
```

- [ ] **Step 3: Run both test classes and verify RED**

```bash
./mvnw -pl data-agent-management \
  -Dtest=DockerClientFactoryTest,DockerImageManagerTest test
```

Expected: compilation fails because the new infrastructure classes do not exist.

- [ ] **Step 4: Implement connector, fallback factory, and image manager**

`DockerClientConnector` is a one-method boundary:

```java
public interface DockerClientConnector {
	DockerClient connect(String host);
}
```

`ZerodepDockerClientConnector` builds `DockerClientConfig`,
`ZerodepDockerHttpClient`, and `DockerClientImpl`, but does not ping or pull.

`DockerClientFactory.create(List<String>)` tries candidates in order, invokes
`client.pingCmd().exec()`, closes failed clients when possible, and throws one
`IllegalStateException` listing all attempted hosts.

`DockerImageManager.ensureAvailable(DockerClient, String)` treats null repository tags as an
empty tag set and waits for a pull to complete when no exact tag is present.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all connection and image branch tests pass without Docker.

- [ ] **Step 6: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker \
  data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/docker
git commit -m "refactor: isolate Docker infrastructure setup"
```

### Task 3: Make Docker executor construction side-effect free

**Files:**
- Create:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/docker/DockerExecutorFactory.java`
- Modify:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/impls/AbstractCodePoolExecutorService.java`
- Modify:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/CodePoolExecutorServiceFactory.java`
- Modify:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code/impls/DockerCodePoolExecutorService.java`
- Replace:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/impls/DockerCodePoolExecutorServiceTest.java`
- Modify:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/CodePoolExecutorServiceFactoryTest.java`

- [ ] **Step 1: Replace environment-dependent executor tests with failing injected tests**

The new test constructs the service directly from a mocked `DockerClient`:

```java
@ExtendWith(MockitoExtension.class)
class DockerCodePoolExecutorServiceTest {

	@Mock
	private DockerClient dockerClient;

	@Test
	void constructor_doesNotCallDocker() {
		DockerCodePoolExecutorService service =
				new DockerCodePoolExecutorService(properties(), dockerClient, false);

		verifyNoInteractions(dockerClient);
		service.close();
	}

	@Test
	void execute_missingContainerWorkDirectory_returnsSpecificFailure() {
		DockerCodePoolExecutorService service =
				new DockerCodePoolExecutorService(properties(), dockerClient, false);

		TaskResponse response = service.execTaskInContainer(new TaskRequest("print(1)", "", ""), "missing");

		assertFalse(response.isSuccess());
		assertEquals("Container 'missing' does not exist work dir", response.exceptionMsg());
		service.close();
	}

	@Test
	void close_closesDockerClientExactlyOnce() throws Exception {
		DockerCodePoolExecutorService service =
				new DockerCodePoolExecutorService(properties(), dockerClient, false);

		service.close();
		service.close();

		verify(dockerClient).close();
	}
}
```

No test may use reflection, catch construction failure, conditionally return, or connect to a
real daemon.

- [ ] **Step 2: Write failing composition-root tests**

```java
@Test
void getObject_docker_delegatesToDockerExecutorFactory() {
	when(properties.getCodePoolExecutor()).thenReturn(CodePoolExecutorEnum.DOCKER);
	when(dockerExecutorFactory.create(properties)).thenReturn(dockerExecutor);

	assertSame(dockerExecutor, factory.getObject());
}
```

Add paired LOCAL and AI_SIMULATION cases so all supported branches are asserted.

- [ ] **Step 3: Run focused tests and verify RED**

```bash
./mvnw -pl data-agent-management \
  -Dtest=DockerCodePoolExecutorServiceTest,CodePoolExecutorServiceFactoryTest test
```

Expected: compilation fails because the injected constructor, `close()`, and
`DockerExecutorFactory` do not exist.

- [ ] **Step 4: Implement composition**

`DockerExecutorFactory`:

```java
@Component
@RequiredArgsConstructor
public class DockerExecutorFactory {

	private final DockerHostResolver hostResolver;
	private final DockerClientFactory clientFactory;
	private final DockerImageManager imageManager;

	public DockerCodePoolExecutorService create(CodeExecutorProperties properties) {
		List<String> hosts = hostResolver.candidates(properties.getHost(), System.getProperty("os.name"));
		DockerClient client = clientFactory.create(hosts);
		try {
			imageManager.ensureAvailable(client, properties.getImageName());
			return new DockerCodePoolExecutorService(properties, client, hostResolver.isRemote(hosts.get(0)));
		}
		catch (RuntimeException exception) {
			closeAfterFailedConstruction(client, exception);
			throw exception;
		}
	}
}
```

`DockerCodePoolExecutorService` keeps only:

```java
public DockerCodePoolExecutorService(CodeExecutorProperties properties, DockerClient dockerClient,
		boolean remote) {
	super(properties);
	this.dockerClient = Objects.requireNonNull(dockerClient, "dockerClient");
	this.isRemote = remote;
	this.containerTempPath = new ConcurrentHashMap<>();
}
```

Remove host resolution, Docker client construction, ping, and image pulling from this class.
Remove per-instance JVM shutdown-hook registration from `AbstractCodePoolExecutorService` and
make it `AutoCloseable`. Add an idempotent `close()` that shuts down the pool; the Docker
override additionally closes the client exactly once.

`CodePoolExecutorServiceFactory` delegates only the DOCKER branch to
`DockerExecutorFactory`.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run the Step 3 command. Expected: all focused tests pass without Docker.

- [ ] **Step 6: Run all code-executor unit tests**

```bash
./mvnw -pl data-agent-management -Dtest='*CodePoolExecutorService*Test' test
```

Expected: all selected tests pass and no Docker connection log appears.

- [ ] **Step 7: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/service/code \
  data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code
git commit -m "refactor: inject Docker executor dependencies"
```

### Task 4: Retain an explicit real-Docker integration check

**Files:**
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/impls/DockerCodePoolExecutorServiceIT.java`

- [ ] **Step 1: Write the opt-in integration test**

```java
@Tag("docker-integration")
class DockerCodePoolExecutorServiceIT {

	@Test
	void create_withAvailableDaemon_preparesExecutor() {
		CodeExecutorProperties properties = new CodeExecutorProperties();
		DockerHostResolver resolver = new DockerHostResolver();
		DockerExecutorFactory factory = new DockerExecutorFactory(resolver,
				new DockerClientFactory(new ZerodepDockerClientConnector()), new DockerImageManager());
		DockerCodePoolExecutorService service = factory.create(properties);
		try {
			assertNotNull(service);
		}
		finally {
			service.close();
		}
	}
}
```

The `*IT` suffix keeps this test out of Surefire's default includes. It is run explicitly:

```bash
./mvnw -pl data-agent-management -Dtest=DockerCodePoolExecutorServiceIT test
```

- [ ] **Step 2: Verify default selection excludes the integration test**

```bash
./mvnw -pl data-agent-management -Dtest=DockerCodePoolExecutorServiceTest test
```

Expected: unit tests pass with Docker stopped or unavailable.

- [ ] **Step 3: Run the integration check when Docker is available**

Run the explicit IT command. Expected: pass, or report the environment failure separately
without weakening the unit test.

- [ ] **Step 4: Commit**

```bash
git add data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code/impls/DockerCodePoolExecutorServiceIT.java
git commit -m "test: separate Docker integration coverage"
```

### Task 5: Correct DTO builder defaults with comparison tests

**Files:**
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/dto/ModelConfigDTOTest.java`
- Create:
  `data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/dto/search/HybridSearchRequestTest.java`
- Modify:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/dto/ModelConfigDTO.java`
- Modify:
  `data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/dto/search/HybridSearchRequest.java`

- [ ] **Step 1: Write constructor-versus-builder comparison tests**

```java
@Test
void builder_usesSameDefaultsAsNoArgsConstruction() {
	ModelConfigDTO direct = new ModelConfigDTO();
	ModelConfigDTO built = ModelConfigDTO.builder().build();

	assertAll(
			() -> assertEquals(direct.getTemperature(), built.getTemperature()),
			() -> assertEquals(direct.getMaxTokens(), built.getMaxTokens()),
			() -> assertEquals(direct.getIsActive(), built.getIsActive()),
			() -> assertEquals(direct.getProxyEnabled(), built.getProxyEnabled()));
}
```

```java
@Test
void builder_usesSameSearchDefaultsAsNoArgsConstruction() {
	HybridSearchRequest direct = new HybridSearchRequest();
	HybridSearchRequest built = HybridSearchRequest.builder().build();

	assertEquals(direct.getSimilarityThreshold(), built.getSimilarityThreshold());
	assertEquals(direct.getVectorWeight(), built.getVectorWeight());
	assertEquals(direct.getKeywordWeight(), built.getKeywordWeight());
	assertEquals(direct.isUseRerank(), built.isUseRerank());
	assertNotSame(direct.getExtraParams(), built.getExtraParams());
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
./mvnw -pl data-agent-management \
  -Dtest=ModelConfigDTOTest,HybridSearchRequestTest test
```

Expected: `ModelConfigDTOTest` fails because Lombok builder ignores field initializers.

- [ ] **Step 3: Mark intended defaults explicitly**

Add `@Builder.Default` to `ModelConfigDTO.temperature`, `maxTokens`, `isActive`, and
`proxyEnabled`, and to `HybridSearchRequest.similarityThreshold`.

- [ ] **Step 4: Run tests and verify GREEN**

Run the Step 2 command. Expected: both comparison tests pass.

- [ ] **Step 5: Recompile and verify warning removal**

```bash
./mvnw -pl data-agent-management -DskipTests compile
```

Expected: no Lombok warning that a builder ignores these five initializing expressions.

- [ ] **Step 6: Commit**

```bash
git add data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/dto \
  data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/dto
git commit -m "fix: preserve DTO defaults in builders"
```

### Task 6: Verify the reliable baseline and audit the resulting diff

**Files:**
- Modify only files identified by failing verification, if required.

- [ ] **Step 1: Run focused affected tests**

```bash
./mvnw -pl data-agent-management \
  -Dtest='Docker*Test,CodePoolExecutorServiceFactoryTest,ModelConfigDTOTest,HybridSearchRequestTest' test
```

Expected: all selected tests pass without requiring Docker.

- [ ] **Step 2: Run the full default backend test suite**

```bash
./mvnw test
```

Expected: build success, zero failures/errors, and completion without blocking in a real
Docker call.

- [ ] **Step 3: Run style and static checks**

```bash
./mvnw spring-javaformat:validate checkstyle:check
```

Expected: build success and zero Checkstyle violations.

- [ ] **Step 4: Verify no frontend changes and no accidental instruction-file commit**

```bash
git diff main...HEAD -- data-agent-frontend
git log --name-only --format= main..HEAD | rg '^(CLAUDE|AGENTS)\\.md$'
```

Expected: both commands produce no output.

- [ ] **Step 5: Re-scan backend test smells**

```bash
rg -n 'if \\([^)]*== null\\) \\{|catch \\(RuntimeException.*\\)|setAccessible\\(true\\)' \
  data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/service/code
```

Expected: no conditional-pass or reflection pattern remains in the changed Docker tests.

- [ ] **Step 6: Review the full diff**

```bash
git diff --check main...HEAD
git diff --stat main...HEAD
git status --short
```

Expected: only intentional backend, test, design, and plan changes are committed. Existing
environment-owned `CLAUDE.md` state may remain outside the commits.
