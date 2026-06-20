# PHASE4-ablation.md — Ablation Matrix (RAG × memory × self-critique)

## What it measures

"Ablation" = turn a feature off and measure how much the benchmark result drops,
so we can attribute the agent's success to specific components instead of guessing.
This phase ships the **full three-axis matrix** from PLAN.md §3:

| Axis | ON | OFF |
|------|----|-----|
| **RAG** (AST retrieval) | `searchCode` tool offered; `SymbolIndex` built | tool withheld; agent must use `listFiles`/`readFile` |
| **memory** (conversation history) | full reason→act→observe transcript kept | amnesiac: only system prompt + task + most recent observation |
| **self-critique** | `CritiqueMode.CRITIC` completion gate | `CritiqueMode.NONE` |

3 axes × on/off = **8 configs**: `BASE`, `RAG`, `MEM`, `CRITIC`, `RAG+MEM`,
`RAG+CRITIC`, `MEM+CRITIC`, `RAG+MEM+CRITIC`.

> The `memory` axis is implemented as `memory/ConversationMemory` (a
> `view(transcript)` strategy: `full()` vs. `recentOnly()`), toggled per cell
> through `BugfixAgentFactory.assemble(..., retrievalEnabled, memoryEnabled)`.
> Isolating it lets the matrix expose interaction effects (e.g. does the critic
> only help once the agent also remembers what it already tried?).

## How to run

```
./gradlew :benchmark:runner:runAblation
```

Honours the same env vars as the CLI/eval (`AGENT_MODEL`, `OLLAMA_BASE_URL`,
`AGENT_CRITIC_MODEL`, `BENCHMARK_TRACE`) plus **`AGENT_ABLATION_REPEATS`** (default
1). It runs the **whole** seeded-bug benchmark `repeats` times per config
(8 × N × K runs), prints a comparison matrix + per-case grid, and writes one
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
  `BugfixAgentFactory.assemble(..., retrievalEnabled, memoryEnabled)` — the 7-arg
  overload — so the ablation measures the shipped agent, not a parallel one.
- **Components:** `core/AblationConfig` (the matrix), `core/memory/ConversationMemory`
  (the memory axis), `benchmark/AblationRunner` (driver/`main`),
  `benchmark/AblationReport` (matrix + grid + CSV). Reuses `EvalHarness`/`EvalReport`
  from Phase 3 (see `docs/PHASE3.md`).
- **Cost:** with 8 configs and a 7B local model this is many minutes; hard cases
  can hit the per-call timeout and end `stop=ERROR` (see the model-timeout note).
  A free hosted relay with a per-day request cap will not survive a full 8×N×K
  run — see the README "Known limitations".
