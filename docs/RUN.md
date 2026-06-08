# RUN.md — running the agent locally

Phase 1 demo: ask the agent a question about a project; the agent calls
`readFile`, reads a file, and produces a final answer.

## Prerequisites

- **JDK 21+** (this project compiles with JDK 22, targets Java 21).
- **Gradle** — not required directly; use the wrapper `./gradlew`.
- **Ollama** — local LLM runtime. The agent talks to it over HTTP at `http://localhost:11434` by default.

## 1. Install Ollama

Windows: https://ollama.com/download/windows → run the installer. After
install, Ollama runs as a background service and serves
`http://localhost:11434`.

Verify:

```bash
curl http://localhost:11434/api/tags
```

You should get back a JSON object (possibly with an empty `models` array if
nothing is pulled yet). If `curl` fails: Ollama isn't running. Open the
Ollama app from the Start menu, or run `ollama serve` in a terminal.

## 2. Pull a tool-capable model

The default model in `AgentConfig.defaults()` is `qwen2.5:7b` —
roughly 4.7 GB, runs on a laptop with 8 GB+ RAM, and emits **structured
Ollama `tool_calls`**, which the agent loop requires. Pull it:

```bash
ollama pull qwen2.5:7b
```

> **Use an *instruct* model, not `qwen2.5-coder`.** The coder variant returns
> tool calls as plain-text JSON in the message body instead of structured
> `tool_calls`; the agent then sees a text-only reply, treats it as the final
> answer, and stops without ever running a tool. Verified against Ollama's raw
> `/api/chat` — the coder model leaves `tool_calls` null.

Smaller fallback if RAM is tight (still an instruct model):

```bash
ollama pull qwen2.5:3b
```

…then override the default via env var when running (see below). **Models
smaller than 3B generally fail tool calling.** If you only have 4 GB RAM and
no GPU, this project is going to struggle regardless.

Confirm the model is loaded:

```bash
ollama list
```

## 3. Build

From the project root (`D:\JavaProject\java-bugfix-agent`):

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` with all unit tests passing (10 tests). The
first build downloads dependencies via the Aliyun mirror configured in
`build.gradle.kts` — direct Maven Central tends to TLS-handshake-fail from
CN networks.

## 4. Run the agent (default prompt)

```bash
./gradlew :agent-cli:run --args="<absolute-or-relative-path-to-some-project>"
```

Example, pointing at the agent's own repo:

```bash
./gradlew :agent-cli:run --args="."
```

The default prompt is *"Read the file PROJECT.md if it exists and briefly
describe what this project does."* The agent should:

1. Decide to call `readFile` with `path="PROJECT.md"`.
2. Receive the file contents.
3. Produce a final-text answer summarising the project.
4. Exit 0.

Expected console output (abridged):

```
[cfg] provider=ollama model=qwen2.5:7b maxIterations=10 root=...
[task] start: Read the file PROJECT.md if it exists and briefly describe ...
[iter] 0
[llm] tool-call readFile {"path":"PROJECT.md"}
[tool] readFile -> # PROJECT.md — Autonomous Java Bug-Fixing Agent …
[iter] 1
[llm] This project is an autonomous Java bug-fixing agent that …
[task] end: COMPLETED iterations=2

=== Result ===
stopReason: COMPLETED
iterations: 2
answer:
This project is an autonomous Java bug-fixing agent …
```

## 5. Run with a custom prompt

```bash
./gradlew :agent-cli:run --args=". \"What build tool does this project use?\""
```

## 6. Override the model / temperature / iteration cap

All knobs live in env vars (see `AgentConfig.fromEnv()`):

| env var | default | notes |
|---|---|---|
| `AGENT_PROVIDER` | `ollama` | only `ollama` is wired in Phase 1 |
| `AGENT_MODEL` | `qwen2.5:7b` | any Ollama model that emits structured `tool_calls` (instruct, not coder) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | change if Ollama runs elsewhere |
| `AGENT_TEMPERATURE` | `0.2` | float, low for deterministic tool use |
| `AGENT_MAX_ITERATIONS` | `10` | hard cap on the loop |

PowerShell:

```powershell
$env:AGENT_MODEL = "qwen2.5:3b"
./gradlew :agent-cli:run --args="."
```

bash:

```bash
AGENT_MODEL=qwen2.5:3b ./gradlew :agent-cli:run --args="."
```

## Troubleshooting

**`stopReason: ERROR` with `model error: java.net.ConnectException`** —
Ollama isn't running on `localhost:11434`. Start the Ollama app or
`ollama serve`.

**`stopReason: ERROR` with `model error: ... model not found ...`** —
the model named in `AGENT_MODEL` isn't pulled. Run `ollama pull <name>`.

**Agent loops to `stopReason: MAX_ITERATIONS`** — the model is calling
tools without ever finishing. Common with very small models (≤3B params)
or models without function-calling training. Try a bigger model
(`qwen2.5:7b` or `llama3.1:8b`).

**`No SLF4J providers were found`** — harmless warning at startup; the
LangChain4j HTTP client logs through SLF4J and we haven't bound a provider
yet. Will be added in Phase 4 with the structured tracer.

**Gradle wrapper fails to download distribution** — the wrapper is pinned
to 9.2.1, which is what's installed locally; it should not need to
download. If something's odd, run `gradle --version` to confirm the
local Gradle install path matches what the wrapper expects, or run with
`-Dorg.gradle.internal.network.retry.max.attempts=5`.

**Maven Central TLS handshake fails** — already mitigated; the build uses
Aliyun mirrors first. If those also fail, try a VPN or set
`-Dhttps.protocols=TLSv1.2` in `JAVA_OPTS`.

## What success looks like for Phase 1

This script:

```bash
./gradlew build && ./gradlew :agent-cli:run --args="."
```

…ends with `stopReason: COMPLETED` and a non-trivial `answer:` block that
clearly came from reading `PROJECT.md`. That's the Phase 1 acceptance
criterion (PROJECT.md §10, PLAN.md §6 Phase 1) met end-to-end.

## Phase 2 demo: fix a seeded bug

`benchmark/projects/calc01/` is a tiny self-contained Gradle project with one
off-by-one bug in `src/main/java/com/example/calc/Average.java` (divides by
`length - 1` instead of `length`). 3 of 5 `AverageTest` tests fail.

Reset the bug to its buggy state if needed (the agent will have overwritten it
on a previous run), then point the agent at it:

```bash
./gradlew :agent-cli:run --args="benchmark/projects/calc01"
```

Expected flow in the console output:

```
[init] indexed N symbols in Xms
[init] test runner: GradleTestRunner
[task] start: There may be a bug …
[iter] 0
[llm] tool-call runTests {}
[tool] runTests -> FAILED (exit=1, …) Failing tests:
  - com.example.calc.AverageTest#averageOfThreeNumbers() …
[iter] 1
[llm] tool-call searchCode {"query":"Average compute"}
[tool] searchCode -> Found 1 matching symbol(s): METHOD com.example.calc.Average.compute …
[iter] 2
[llm] tool-call readFile {"path":"src/main/java/com/example/calc/Average.java"}
[tool] readFile -> package com.example.calc; …
[iter] 3
[llm] tool-call writeFile {"path":"src/main/java/com/example/calc/Average.java","content":"…fixed…"}
[tool] writeFile -> OK: N bytes written …
[iter] 4
[llm] tool-call runTests {}
[tool] runTests -> PASSED (exit=0, …)
[iter] 5
[llm] Fixed the off-by-one in Average.compute: was dividing by length-1, now divides by length.
[task] end: COMPLETED iterations=6

=== Result ===
stopReason: COMPLETED
iterations: 6
answer:
Fixed the off-by-one in Average.compute …
```

If the agent gets stuck (loops, edits the wrong file, refuses to use tools),
try a larger model or raise `AGENT_MAX_ITERATIONS`. The qwen2.5:7b model
handles this bug reliably; smaller models may struggle.

**To re-run after the agent succeeds**, the buggy file has been overwritten —
restore it with:

```bash
git checkout benchmark/projects/calc01/src/main/java/com/example/calc/Average.java
```

(or whatever your VCS state is; this repo isn't a git repo yet).
