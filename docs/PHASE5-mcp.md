# PHASE5-mcp.md — MCP Server (expose the agent's tools)

## What it is

`agent-mcp` is a **stdio MCP server** that exposes this project's tools —
`readFile`, `writeFile`, `listFiles`, `searchCode`, `runTests` — to any MCP
client (an IDE, Claude Desktop, another agent) over JSON-RPC on stdin/stdout.

The point of Phase 5: the tools are **transport-agnostic**. The exact same
`@Tool` objects the in-process agent uses (`FileTools`, `SearchCodeTool`,
`TestRunnerTool`) are served here unchanged — only the transport differs
(in-process method calls → MCP/JSON-RPC). An external LLM now drives the agent's
toolset against a target project.

## How to run

```
./gradlew :agent-mcp:run --args="<absolute-project-path>"
```

The process speaks MCP on stdin/stdout and blocks until the client disconnects.
All diagnostics go to **stderr** — stdout is the protocol channel and must stay
clean. Point an MCP client at this command with the project path as its argument.

## Design

```
MCP client (IDE / Claude Desktop / agent)
        │  JSON-RPC over stdio
        ▼
StdioMcpServerTransport ── McpServer ── @Tool objects (FileTools, SearchCodeTool,
   (langchain4j-community)                              TestRunnerTool)
        │                                                     │
        └── tools/list, tools/call ──────────────────────────┘ operate on <projectPath>
```

- **`McpServerTools.forProject(projectRoot)`** — pure assembly of the tool
  objects (same set the agent uses; `runTests` omitted when no Gradle/Maven build
  is detected). No transport, fully unit-tested.
- **`McpServerMain`** — builds the tools, wraps them in `McpServer`, and serves
  them via `StdioMcpServerTransport(System.in, System.out, server)`, then
  `awaitClose()`.
- **Library:** `dev.langchain4j:langchain4j-community-mcp-server` (the community
  *server* module; the core `langchain4j-mcp` is client-only). It maps `@Tool`
  methods to MCP tool schemas automatically via `McpToolSchemaMapper`.

## Dependency / version note

The community MCP-server module is published on its own line (`1.15.0-betaN`),
not the core `1.2.0` pin (see the langchain4j-version memory). It is **isolated
to `agent-mcp`**: that module resolves langchain4j to 1.15.0, while `agent-core`,
`agent-cli`, and `benchmark` stay on 1.2.0. This is safe because **the MCP server
runs no LLM** — the ollama tool-call serialization bug that motivated the 1.2.0
pin cannot occur here. Verified: `agent-core` runtimeClasspath = 1.2.0;
`agent-mcp` = 1.15.0.

## Tests

`McpServerToolsTest` (tool assembly; `runTests` present/absent by build detection)
and `McpServerSmokeTest` — the Phase 5 acceptance smoke test — which drives
`McpServer.handle(JsonNode)` with real JSON-RPC (`initialize`, `tools/list`,
`tools/call readFile`) and asserts the file contents come back over the protocol,
without needing a live stdio pipe.
