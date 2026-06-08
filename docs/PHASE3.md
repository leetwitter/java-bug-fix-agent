# PHASE3.md — Evaluation Harness

Status: **implemented and verified end-to-end** on the calc01 benchmark.

Phase 3 delivers the project's headline metric: a single command that runs the
agent over the seeded-bug benchmark and reports a **resolution rate**.

## 1. What it does

```
./gradlew :benchmark:runner:runEval
```

For every benchmark case it: copies the (already-buggy) target project to a
throwaway temp workspace → assembles and runs the agent there → **independently**
runs the project's tests to decide `resolved` → records metrics → deletes the
workspace. Then it prints a summary and writes a CSV under `reports/`.

Because each run works on a copy, the template projects are **never mutated** —
the old "restore the fixture after every demo" chore is gone.

## 2. Module: `benchmark/runner`

New Gradle subproject (`include("benchmark:runner")`), depends on `agent-core`.

| Class | Role |
|-------|------|
| `BenchmarkCase` | one seeded bug: `(id, templateDir, prompt)` |
| `BenchmarkCatalog` | discovers cases — every project-shaped subdir of `benchmark/projects` |
| `BugWorkspace` | `AutoCloseable` temp copy of a target project; skips `build`/`.gradle`/`.git`; deletes on close |
| `EvalHarness` | orchestrates copy → run → grade → record; `AgentRunner`/`Grader` injected for testability |
| `EvalResult` | per-case: `resolved, completed, iterations, wallMillis, stopReason` |
| `EvalReport` | aggregates → resolution rate, human summary, CSV |
| `EvalRunner` | `main` for `runEval`: wires the real agent + `TestRunner` grader |

## 3. The key design choice: independent grading

`resolved` is decided **only** by whether the project's tests pass after the
agent runs — never by the agent's own COMPLETED claim. The two are recorded
separately (`resolved` vs `completed`) so the harness surfaces **false
positives**: a run where the agent declared success but the tests are still red.

This is the same separation argued for the critic (`docs/PHASE4-critic.md`): the
agent fixes; an independent oracle judges. Here the oracle is the test suite's
exit code — deterministic, no LLM-as-judge.

## 4. Configuration

Honours the same env vars as the CLI, so a before/after comparison is two runs
with a different `AGENT_CRITIQUE`:

| Env var | Effect |
|---------|--------|
| `AGENT_MODEL` | repair model (default `qwen2.5:7b`) |
| `AGENT_CRITIQUE` | `none` \| `self` \| `critic` — labels the run + CSV `config` column |
| `AGENT_CRITIC_MODEL` | critic model when critique=critic |
| `BENCHMARK_TRACE` | `true` prints the per-case agent trace (default quiet) |

CSV schema (ablation-ready — concatenate runs):
```
config,bug_id,resolved,completed,iterations,wall_clock_ms,stop_reason
```

## 5. Shared assembly (validity)

`App` (CLI) and `EvalHarness` both build the agent through
`BugfixAgentFactory.assemble(...)` in `agent-core`. The benchmark therefore
measures the **same** agent that ships — system prompt, tools, critic wiring and
all. The bug-fix system prompt and default prompt now live in the factory.

## 6. Verified

- Unit (model-free): `BugWorkspaceTest` (copies sources, skips build dirs, cleans
  up, leaves template intact), `BenchmarkCatalogTest` (discovery), `EvalReportTest`
  (rate/CSV/summary), `EvalHarnessTest` (orchestration; grading independent of the
  agent's COMPLETED claim; agent exceptions recorded not propagated).
- Live (calc01, critique=critic): two consecutive runs produced **UNRESOLVED then
  RESOLVED** — real model variance, faithfully captured. The RESOLVED trace even
  shows the model drop `package` on one `writeFile` (tests red), self-correct on
  the next, then `[critic] APPROVE`. Templates confirmed untouched afterward.

## 7. Next

- **More bugs**: grow `benchmark/projects` toward the 30–50 target (just drop in
  seeded projects; the catalog finds them).
- **pass@k**: the variance above argues for running each case k times and
  reporting pass@k, not a single shot.
- **Tokens**: `RunResult` does not yet carry token usage; wire it (sum
  `ChatResponse.tokenUsage()` in `Agent`) so the ablation can report token cost —
  most relevant when comparing critique modes.
- **Phase 4 `AblationRunner`**: run this harness under each `AGENT_CRITIQUE` (and
  RAG/memory toggles) and emit the ablation table.
