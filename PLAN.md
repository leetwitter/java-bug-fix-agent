# PLAN.md — Implementation Plan (supersedes parts of PROJECT.md)

> Companion to `PROJECT.md`. Where this document and PROJECT.md disagree, **this
> document wins** for: build tool, retrieval strategy, ablation scope, and MCP.
> Everything else (goals, success criteria §2, guardrails §8, metrics §11) is
> inherited unchanged from PROJECT.md.

---

## 0. Deviations from PROJECT.md (read this first)

| Topic | PROJECT.md says | PLAN.md decision | Reason |
|---|---|---|---|
| Build tool | Maven (§3) | **Gradle (Kotlin DSL)** | User requested on 2026-06-05. |
| Primary RAG | Dense embeddings + pgvector (§3, §4) | **Symbol/AST-aware via JavaParser** | Stronger for Java; chosen by user. |
| Ablation scope | One before/after in Phase 4 (§10) | **Full matrix: RAG × memory × self-critique** | Stronger evaluation story. |
| MCP server | Stretch goal (§10) | **Core deliverable in Phase 5** | Requested by user. |
| Dense retrieval | Required | **Optional, behind `Retriever` interface** | Kept as future comparison point. |

PROJECT.md sections still authoritative: §1 Goals, §2 Success Criteria, §6
Benchmark Design, §7 Component Contracts (subject to §0 changes), §8 Guardrails,
§9 Conventions (with `mvn` → `./gradlew`), §11 Metrics, §12 Guidance.

---

## 1. Updated Tech Stack

- **Language:** Java 21 (toolchain pinned in Gradle).
- **Build tool:** Gradle 8.x with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`). Wrapper committed.
- **Agent framework:** LangChain4j 1.x
  - `dev.langchain4j:langchain4j` (core)
  - `dev.langchain4j:langchain4j-agentic`
  - Provider: `langchain4j-ollama` (dev) + `langchain4j-open-ai` (final benchmark) — selected at runtime via `ChatModelFactory`.
  - **Removed from core deps:** `langchain4j-embeddings`, `langchain4j-pgvector` (move to optional Phase 5+ comparison module).
- **Retrieval:** `com.github.javaparser:javaparser-symbol-solver-core` (3.26.x) — primary symbol indexer.
- **MCP:** official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp` or LangChain4j's MCP module — pick whichever is current at implementation time; verify with a quick search before adding the dependency).
- **Target-project test execution:** JUnit 5 in the seeded benchmark projects. `TestRunner` must support both Gradle and Maven *target* projects (the harness's own build is Gradle; targets may be either).
- **Testing the agent itself:** JUnit 5 + Mockito + AssertJ.
- **Tracing/observability:** structured JSON per-run file (Jackson). No heavyweight framework.

---

## 2. Updated Repository Structure

```
java-bugfix-agent/
├── settings.gradle.kts
├── build.gradle.kts                 # root (multi-module)
├── gradle/                          # wrapper
├── gradlew, gradlew.bat
├── PROJECT.md
├── PLAN.md                          # this document
├── README.md                        # final, with headline metric + ablation table
├── CLAUDE.md                        # conventions (Gradle commands etc.)
│
├── agent-core/                      # the agent + tools (library)
│   └── src/main/java/com/example/agent/
│       ├── core/        Agent.java, AgentConfig.java, StopCondition.java,
│       │                AblationConfig.java   # toggles RAG / memory / self-critique
│       ├── llm/         ChatModelFactory.java
│       ├── tools/       FileTools.java, SearchCodeTool.java, TestRunnerTool.java
│       ├── rag/         SymbolIndexer.java, SymbolIndex.java, Retriever.java (iface)
│       ├── context/     ContextManager.java
│       ├── memory/      ConversationMemory.java     # toggleable
│       ├── critique/    SelfCritique.java           # toggleable
│       ├── observability/ Tracer.java
│       └── exec/        TestRunner.java, GradleTestRunner.java, MavenTestRunner.java
│
├── agent-cli/                       # CLI entry point (App.java)
│
├── agent-mcp/                       # MCP server exposing the same tools  ← new
│   └── src/main/java/com/example/agent/mcp/
│       └── McpServerMain.java
│
├── benchmark/
│   ├── projects/                    # 2-3 small Java target projects
│   ├── bugs/                        # seeded-bug definitions (JSON/YAML)
│   └── runner/                      # EvalHarness + AblationRunner
│       └── src/main/java/com/example/benchmark/
│           ├── EvalHarness.java
│           └── AblationRunner.java                  # runs the full matrix
│
└── reports/                         # generated metrics + ablation tables (gitignored except samples)
```

Rationale for multi-module: the MCP server and CLI both depend on `agent-core`
but have different runtime needs; the benchmark depends on `agent-core` but
shouldn't ship in the CLI distribution.

---

## 3. Ablation Matrix (the new evaluation centerpiece)

PROJECT.md §10 Phase 4 calls for *a* before/after. We are going further:
run the full benchmark under each of these configurations and report a table.

| Config name | RAG (symbol search) | Conv. memory | Self-critique |
|---|:-:|:-:|:-:|
| `baseline`        | off | off | off |
| `rag_only`        | on  | off | off |
| `memory_only`     | off | on  | off |
| `critique_only`   | off | off | on  |
| `rag+memory`      | on  | on  | off |
| `rag+critique`    | on  | off | on  |
| `memory+critique` | off | on  | on  |
| `full`            | on  | on  | on  |

**Why all 8.** Lets us isolate each feature's marginal contribution and detect
interaction effects (e.g., does self-critique only help when RAG is on?). If
the full 2³ matrix is too expensive at benchmark time, fall back to a one-at-a-time
ablation (`full` minus each feature) — still 4 runs instead of 8.

**Implementation:**
- `AblationConfig` is a plain record with three booleans, plumbed into `Agent`.
- When `RAG=off`, `SearchCodeTool` is removed from the registered tool set entirely (don't just return empty — we want to test the agent's behavior without the tool existing).
- When `memory=off`, every iteration sees only the original task + the most recent observation (no conversation history).
- When `self-critique=off`, the agent acts directly on its own plan; when on, an extra "critique your proposed edit before writing" step is inserted.
- `AblationRunner` iterates over the 8 configs × N bugs and writes one CSV with `config,bug_id,resolved,iterations,tokens,wall_clock_ms`.
- Final output: a markdown table summarizing resolution rate per config, ready to paste into README.

---

## 4. MCP Server (promoted to core)

Expose all five agent tools (`readFile`, `writeFile`, `listFiles`, `searchCode`,
`runTests`) over MCP so the toolset is reusable.

- Tool implementations live in `agent-core/tools/` and are **transport-agnostic** — plain Java methods/classes.
- `agent-core` registers them with LangChain4j's `@Tool` annotations for in-process use.
- `agent-mcp` wraps the same implementations as MCP tool handlers.
- Run command: `./gradlew :agent-mcp:run` (or a built jar) — produces a stdio MCP server by default, with a flag for SSE/HTTP transport if the SDK supports it cheaply.
- Smoke test: a tiny end-to-end test that spins up the MCP server and calls `readFile` over the protocol.

This satisfies "tool reusability" cleanly without forcing the bug-fixing loop
itself to go over MCP (which would add latency and complexity for no benefit).

---

## 5. Symbol/AST-aware Retrieval (replaces dense vectors as primary)

`SymbolIndexer` (using JavaParser + symbol solver):
- Parses every `.java` file in the target project.
- Emits a `Symbol` record per class / method / field with: FQN, kind, file path, line range, brief signature, surrounding-context snippet.
- Builds reverse indexes: identifier → symbols; symbol → references; test class → suspected source classes (via test-name heuristics + symbol references).

`SearchCodeTool` (LangChain4j `@Tool`):
- Query API: free-text identifiers; the tool extracts identifier-like tokens and looks them up in the symbol index, then ranks by (a) name match quality, (b) whether the symbol is referenced by the failing test, (c) symbol kind preference (methods > fields > classes for bug-fix queries).
- Returns top-k symbols with their source snippets, formatted compactly.

`Retriever` interface: behind a single interface so a future dense/BM25 impl can
be slotted in for comparison. The interface is the seam — the comparison itself
is out of scope for now per user's selection.

### 5.1 Why AST over dense vectors (rationale for the swap)

Dense-vector retrieval (PROJECT.md's original choice) treats code as **text** and
matches by semantic similarity. AST retrieval treats code as a **structured
program** and matches by symbol references. For Java bug-fixing specifically,
the structural approach is a stronger baseline:

| | Dense vectors + pgvector | AST + JavaParser |
|---|---|---|
| Index unit | Text chunk (e.g. 500 chars) | Symbol (class / method / field) |
| Stored per unit | One float vector (~768 dim) | FQN, signature, references, source range |
| Match by | Cosine similarity in embedding space | Exact identifier match + reference graph |
| Deps | Embedding model + vector DB | JavaParser + in-memory index |
| Source of "understanding" | Statistical patterns from training corpus | Java language semantics (compiler-grade) |

**Why this matters for bug-fixing queries.** The retrieval inputs we actually
have are inherently structural:
- A failing test name → maps to a method under test by naming convention + symbol references.
- A stack trace → exact file and line.
- An identifier from an error message → exact lookup.

These are all first-class operations on a symbol index; on a vector store they
have to be approximated through fuzzy similarity. AST retrieval also returns
**whole methods** (not chopped-up text windows) and can answer "who calls X"
— useful for impact analysis after an edit.

**Where dense wins** (and why we don't need it for v1): natural-language queries
("find the auth code"), cross-codebase fuzzy matching, and retrieving comments
or docs. None of these are on the critical path for resolving a failing JUnit
test.

**Why keep the `Retriever` seam.** If we later want to claim "AST beats dense
by N%" as an ablation row, a `DenseRetriever` implementation can be dropped in
without touching `Agent` or `SearchCodeTool`. That comparison is optional Phase 5
work; not building it now keeps the core dependency footprint small (no
embedding model, no Postgres, no pgvector).

---

## 6. Phased Plan (revised)

Same five phases as PROJECT.md §10, with deliverables updated for the deviations
above. Acceptance criteria from PROJECT.md still apply unless noted.

### Phase 1 — Foundations (≈Weeks 1–3)
- Gradle multi-module project (root + `agent-core` + `agent-cli`) with wrapper, Java 21 toolchain.
- `ChatModelFactory` against Ollama (env-driven config; no hard-coded provider).
- One working `@Tool` (`readFile`).
- Bare `Agent` loop with `MaxIterations` stop condition.
- Unit tests for `Agent` using a mocked chat model.
- **Acceptance:** PROJECT.md §10 Phase 1 acceptance, runnable via `./gradlew :agent-cli:run`.

### Phase 2 — Tools + AST retrieval + first green run (≈Weeks 4–6)
- `FileTools` (read/write/list) with write-path guardrails (no writes to `**/src/test/**`).
- `TestRunner` abstraction + `GradleTestRunner` + `MavenTestRunner` (shell out, parse output).
- `SymbolIndexer` + `SymbolIndex` + `SearchCodeTool` (JavaParser-based).
- One easy seeded bug fixed end-to-end.
- **Acceptance:** PROJECT.md §10 Phase 2 acceptance — agent fixes one easy bug autonomously.

### Phase 3 — Benchmark + harness (≈Weeks 7–9)
- 30–50 seeded bugs across 2–3 target projects, per PROJECT.md §6 schema.
- `EvalHarness`: resets state per run, applies iteration cap, records resolved/iterations/tokens/wall-clock.
- Outputs a single metrics summary (CSV + markdown table).
- **Acceptance:** PROJECT.md §10 Phase 3 acceptance — one command → benchmark summary.

### Phase 4 — Reliability, observability, ablation matrix (≈Weeks 10–12)
- All guardrails from PROJECT.md §8 (test-file write block, no-progress detection, arg validation, malformed-call recovery).
- `Tracer` writing per-run JSON trace.
- `ContextManager` with summarize/prune + rolling window; tested on the largest test logs in the benchmark.
- `ConversationMemory` and `SelfCritique` implemented as toggleable components.
- `AblationConfig` + `AblationRunner` produce the 8-config (or 4-config fallback) ablation table.
- **Acceptance:** no crashes on bad tool calls or huge logs; every run inspectable; ablation table generated end-to-end with at least one positive marginal contribution measured.

### Phase 5 — MCP server, polish, story (≈Weeks 13–14)
- `agent-mcp` module: stdio MCP server exposing all tools; smoke test.
- README: headline resolution rate, architecture diagram, **ablation table**, MCP usage example, CLI demo.
- (Optional / time-permitting per PROJECT.md §3) add a dense-vector `Retriever` impl behind the existing interface as a quick comparison row in the ablation.
- **Acceptance:** PROJECT.md §10 Phase 5 acceptance; MCP server reachable; README tells the full story.

---

## 7. Conventions (delta from PROJECT.md §9)

- **Build:** `./gradlew build`
- **Unit tests:** `./gradlew test`
- **Run agent on one project:** `./gradlew :agent-cli:run --args="<projectPath>"`
- **Run full benchmark:** `./gradlew :benchmark:runner:runEval`
- **Run ablation matrix:** `./gradlew :benchmark:runner:runAblation`
- **Run MCP server:** `./gradlew :agent-mcp:run`
- Everything else (style, secrets via env vars, every component gets a unit test) from PROJECT.md §9 still applies.

---

## 8. Open Questions (answer before Phase 2 starts)

1. **Ollama model choice for dev** — default to `qwen2.5-coder:7b` or `llama3.1:8b`? Affects tool-calling reliability.
2. **Hosted provider for final run** — OpenAI (`gpt-4o-mini`/`gpt-4.1-mini`), Anthropic Claude (`claude-haiku-4-5`), or both for a provider comparison row?
3. **Target projects for benchmark** — write 2–3 small ones from scratch, or fork an existing tiny OSS Java lib (e.g., a small utility library) and seed bugs into it? Writing from scratch is faster; forking gives more realistic code.
4. **Ablation matrix size at benchmark time** — full 8 configs × 50 bugs = 400 runs (expensive against hosted API). Acceptable, or stick to the 4-config one-at-a-time fallback for the headline number?

These don't block Phase 1 bootstrap — they need answers before Phase 3.
