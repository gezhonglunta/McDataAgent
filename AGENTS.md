<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->

## Project Shape

- Backend is the only Maven module: `data-agent-management`; app entrypoint is `com.alibaba.cloud.ai.dataagent.DataAgentApplication` and it runs on port `8065`.
- Frontend is separate, not a Maven module: `data-agent-frontend-nuxt` is a Nuxt 4/Vue 3/Vuetify 3 app, SPA-only (`ssr: false`), with `/api/**` and `/nl2sql/**` proxied to `http://localhost:8065`.
- The main runtime flow is a Spring AI Alibaba `StateGraph` wired in `DataAgentConfiguration`: REST/SSE enters `GraphController` -> `GraphServiceImpl` -> workflow nodes under `workflow/node` with routing in `workflow/dispatcher`.
- Management data is stored in the configured datasource; analyzed business data sources are separate and configured through the app.

## Commands

- Backend dev server from repo root: `./mvnw -pl data-agent-management spring-boot:run` (PowerShell: `.\mvnw.cmd -pl data-agent-management spring-boot:run`).
- Backend focused compile/package: `./mvnw -pl data-agent-management -DskipTests package`.
- Backend single test, if tests are present: `./mvnw -pl data-agent-management -Dtest=ClassNameTest test`.
- Python sandbox unit verification command from docs: `./mvnw -pl data-agent-management -Dtest='PythonDependencyMetadataParserTest,PythonSandboxBootstrapBuilderTest,SandboxExecutionResultParserTest,SaaSandboxPythonCodeExecutorServiceTest,SaaSandboxRuntimeTest,PythonExecuteNodeTest,PythonWorkflowIntegrationTest' test`.
- Real sandbox integration test requires Docker first: `docker info`, then `./mvnw -pl data-agent-management -Dtest=SaaSandboxTaskRunnerIT test`.
- Frontend commands from `data-agent-frontend-nuxt`: `pnpm install`, `pnpm dev`, `pnpm build`, `pnpm test:unit`, `pnpm gen:ctx`.
- CI-style Java checks are Make targets backed by `CI/make/*.mk` and expect Unix tools plus `mvnd`: `make format-check`, `make checkstyle-check`, `make test`; use Maven wrapper equivalents when those tools are unavailable.

## Setup Gotchas

- Required versions from docs/config: JDK 17+, Spring Boot 3.4.8, Node 22+, pnpm 11+; frontend README says Node 20+, but root docs and `package.json` dependency align on newer tooling.
- Default backend profile expects MySQL at `saa_data_agent`; SQL auto-init is disabled by default (`DATA_AGENT_DATASOURCE_SQL_INIT=never`). Import `data-agent-management/src/main/resources/sql/schema.sql` and `data.sql`, plus product sample SQL when needed, or explicitly set `DATA_AGENT_DATASOURCE_SQL_INIT=always`.
- For in-memory local backend data, use `SPRING_PROFILES_ACTIVE=h2`; it loads `sql/h2/*` and enables the H2 console.
- Vector store defaults to Spring AI `simple`; ready profiles exist for `SPRING_PROFILES_ACTIVE=milvus` and `SPRING_PROFILES_ACTIVE=elasticsearch`.
- Python workflow execution requires Docker and creates task-scoped containers with prefix `dataagent-sandbox-`; check cleanup with `docker ps -a --filter name=dataagent-sandbox-`.
- On Windows, local file storage config intentionally uses `uploads` rather than `./uploads`; avoid reintroducing dotted relative paths in that setting.

## Style And Workflow

- Java formatting/checkstyle is enforced from root `pom.xml`: Spring Java Format, Checkstyle config at `CI/src/checkstyle/checkstyle.xml`, Spotless removes unused imports and applies the Apache header during compile.
- Java files use tab indentation after Spring Java Format; license headers are expected on source/config files matching existing patterns.
- Frontend Prettier uses tabs, semicolons, and single quotes; ESLint deliberately allows single-word Vue components, non-self-closing HTML, `v-html`, and Vuetify `v-slot:item.xxx` modifiers.
- Frontend follows Folder-as-Context: update nearby module `README.md`/JSDoc and run `pnpm gen:ctx` when changing documented services, components, composables, pages, or utils.
