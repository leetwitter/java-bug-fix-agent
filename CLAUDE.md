# CLAUDE.md — short conventions

Source-of-truth docs: `PROJECT.md` (goals, success criteria) and `PLAN.md`
(deviations + current phase plan). Read those when context is needed.
For local-run instructions (Ollama setup + demo), see `docs/RUN.md`.

## Commands

- Build: `./gradlew build`
- Unit tests: `./gradlew test`
- Run agent on a project: `./gradlew :agent-cli:run --args="<projectPath> [<prompt>]"`
- (Phase 3+) Run benchmark: `./gradlew :benchmark:runner:runEval`
- (Phase 4+) Run ablation matrix: `./gradlew :benchmark:runner:runAblation`
- (Phase 5+) Run MCP server: `./gradlew :agent-mcp:run`

## Environment variables

- `AGENT_PROVIDER` — `ollama` (default) | `openai`
- `AGENT_MODEL` — model name (default: `qwen2.5:7b` for Ollama; use an
  *instruct* model — the `qwen2.5-coder` variant does not emit structured
  tool_calls and breaks the agent loop)
- `AGENT_TEMPERATURE` — float, default `0.2`
- `AGENT_MAX_ITERATIONS` — int, default `10`
- `OLLAMA_BASE_URL` — default `http://localhost:11434`
- `OPENAI_API_KEY` — required when `AGENT_PROVIDER=openai`
- `OPENAI_BASE_URL` — optional; point `openai` at any OpenAI-compatible endpoint
  (Azure OpenAI, DeepSeek, Moonshot, a local proxy). Unset → `api.openai.com`.
- `AGENT_MIN_CALL_INTERVAL_MS` — optional; min ms between LLM calls
  (`ThrottledChatModel`). Client-side throttle for **RPM**-limited relays; does
  *not* help per-day request caps (see the chatanywhere 200/day note). Default
  `0` = off.

## Style

- Java 21 sources; compile via JDK 22 with `--release 21`.
- Small classes, constructor injection, interfaces at seams (`ChatModel`,
  `Retriever`, `TestRunner`, `Tracer`) for mockability.
- Tools are LangChain4j `@Tool`-annotated methods on plain Java objects;
  implementations are transport-agnostic (in-process today, MCP in Phase 5).
- Secrets via env vars only. Never commit.
- Every new component gets a unit test before moving on.

## Layout

- `agent-core/` — agent loop, tools, retrieval, context mgmt, observability.
- `agent-cli/` — CLI entry point.
- `agent-mcp/` — (Phase 5) MCP server exposing the same tools.
- `benchmark/` — (Phase 3+) seeded bugs, EvalHarness, AblationRunner.
