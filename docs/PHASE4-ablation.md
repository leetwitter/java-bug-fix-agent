# PHASE4-ablation.md — Ablation Matrix (RAG × self-critique)

## What it measures

"Ablation" = turn a feature off and measure how much the benchmark result drops,
so we can attribute the agent's success to specific components instead of guessing.
This phase ships the **two implemented axes**:

| Axis | ON | OFF |
|------|----|-----|
| **RAG** (AST retrieval) | `searchCode` tool offered; `SymbolIndex` built | tool withheld; agent must use `listFiles`/`readFile` |
| **self-critique** | `CritiqueMode.CRITIC` completion gate | `CritiqueMode.NONE` |

2 axes × on/off = **4 configs**: `BASE`, `RAG`, `CRITIC`, `RAG+CRITIC`.

> The `memory` axis from PLAN.md is intentionally deferred: there is no cross-turn
> memory component yet. `AblationConfig` is shaped so adding it later becomes a
> third field + a 2×2×2 `matrix()` without touching callers.

## How to run

```
./gradlew :benchmark:runner:runAblation
```

Honours the same env vars as the CLI/eval (`AGENT_MODEL`, `OLLAMA_BASE_URL`,
`AGENT_CRITIC_MODEL`, `BENCHMARK_TRACE`) plus **`AGENT_ABLATION_REPEATS`** (default
1). It runs the **whole** seeded-bug benchmark `repeats` times per config
(4 × N × K runs), prints a comparison matrix + per-case grid, and writes one
combined CSV to `reports/ablation-<timestamp>.csv`.

**Repeats matter.** A 7B local model is non-deterministic — the same config can
resolve a case on one run and miss it on the next. A single run per cell is noise;
set `AGENT_ABLATION_REPEATS=3` (or 5) so the matrix reports a *mean* rate with its
spread instead of a coin-flip. Cost scales linearly with K.

## Output

- **Matrix table** — one row per config: mean `resolve_rate` ± sample std-dev
  across the K runs, mean `false_pos` (agent said COMPLETED but the oracle
  disagrees), mean wall-clock.
- **Per-case grid** — rows = cases, columns = configs, cell = `k/K` (how many of
  the K runs resolved that case). Reading a row left-to-right shows which feature
  raises a given bug's resolve rate; `k<K` flags a flaky case.
- **CSV** — `config,run,bug_id,resolved,completed,iterations,wall_clock_ms,stop_reason`,
  every raw run (with its `run` index) under one header for spreadsheet pivots.

## Design notes

- **Independent grading is preserved.** Resolution is decided only by the test
  oracle (`TestRunner.runTests().passed()`), never by the agent's own claim. The
  `completed` flag only feeds the `false_pos` column — this is where the critic
  axis is expected to earn its keep (catching false positives such as gcd03's
  COMPLETED-but-UNRESOLVED in the baseline).
- **Same assembly path as production.** Each cell builds the agent through
  `BugfixAgentFactory.assemble(..., retrievalEnabled)` — the new 6-arg overload —
  so the ablation measures the shipped agent, not a parallel one.
- **Components:** `core/AblationConfig` (the matrix), `benchmark/AblationRunner`
  (driver/`main`), `benchmark/AblationReport` (matrix + grid + CSV). Reuses
  `EvalHarness`/`EvalReport` from Phase 3 (see `docs/PHASE3.md`).
- **Cost:** with 4 configs and a 7B local model this is many minutes; hard cases
  can hit the per-call timeout and end `stop=ERROR` (see the model-timeout note).
