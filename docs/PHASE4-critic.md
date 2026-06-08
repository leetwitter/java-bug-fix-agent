# PHASE4-critic.md — Separate Critic Agent (completion gate)

Status: **implemented (CRITIC mode), wired end-to-end, unit-tested.**
Belongs to the Phase 4 ablation axis "self-critique" (`PLAN.md §3`).

## 1. Problem

The repair loop in `Agent.run()` declared success the moment the model stopped
calling tools:

```java
if (!aiMessage.hasToolExecutionRequests()) { return RunResult.completed(...); }
```

Nothing verified the fix. With a local 7B model this produced **false positives**
— most memorably a run where the model dropped the `package` declaration on
`writeFile`, broke compilation, then emitted text claiming success and the loop
believed it. See memory `project_writefile_package_dropping`.

Letting the repair agent grade itself (pure self-critique) is weak: the same
model that made the mistake tends to bless it. We want an **independent
reviewer**.

## 2. Design: a critic as a "completion gate"

A separate `Critic` reviews the proposed fix at exactly the point the loop would
otherwise finish. APPROVE → finish. REVISE → inject the feedback as a user
message and run another round.

```
repair-agent loop:
  chat → tool calls? ──yes──→ execute tools → feed back → next iter
            │ no (wants to finish)
            ▼
      Critic.review(task, changedCode, lastTestOutput)
            │
      APPROVE ┴ REVISE(feedback)
        │            │
   COMPLETED    inject feedback as UserMessage, continue (capped)
```

Why the completion gate (not after every `writeFile`): it targets the exact
false-positive moment, costs ~1–2 extra model calls per task instead of one per
edit, and is a minimal change to the loop.

## 3. Anti-leakage rule (the core constraint)

The critic is part of the **repair system** (`agent-core`), never part of the
evaluation harness. It may only ever see what a developer fixing the bug
**without the answer key** would see:

| Critic MAY see                              | Critic MUST NOT see                         |
|---------------------------------------------|---------------------------------------------|
| the task / prompt                           | the benchmark's known-good reference solution |
| the current content of files it changed     | any held-out grading suite                  |
| the public `runTests` output it itself ran  | the harness's `resolved` verdict            |

This is enforced at the **type level**: `Critic.review(String task, String
changedCodeView, String lastTestOutput)` has no parameter through which a
reference solution could be passed. `EvalHarness` stays the independent,
program-based judge (test exit code); it does not call the critic and is not
called by it.

## 4. Components (all in `agent-core`)

| Type | Package | Role |
|------|---------|------|
| `Critic` | `critic` | interface; `review(...) → Critique`; `Critic.noop()` approves all |
| `Critique` | `critic` | record `(boolean approved, String feedback)` |
| `CritiqueMode` | `critic` | `NONE` / `SELF` (reserved) / `CRITIC`; `fromString` for env |
| `LlmCritic` | `critic` | LLM-backed critic; one chat call, text verdict contract |
| `EditJournal` | `core` | records files written so the critic can review them |

### LlmCritic output contract

A **text** contract, not a structured tool call: the reply's first non-blank
line is `VERDICT: APPROVE` or `VERDICT: REVISE`, the rest is feedback.

- Rationale: the verdict is low-stakes, so we **fail open** — any unparseable or
  empty reply is treated as APPROVE so a confused critic never deadlocks the
  loop. `LlmCritic.parse` is package-private and unit-tested directly.
- Fail-open extends to **errors**, not just bad parses: the loop wraps
  `critic.review(...)` in `Agent.run()` in a try/catch, so if the critic's own
  LLM call throws (timeout, transport error) the exception cannot escape `run()`
  and crash the harness — it is treated as APPROVE. Covered by
  `AgentCritiqueTest.aCriticThatThrowsFailsOpenInsteadOfCrashingTheLoop`.
- A structured single-tool (`submitReview`) variant is possible if false
  approvals from sloppy formatting become a problem; text was chosen for
  simplicity and zero new dependencies.

## 5. Loop changes (`Agent.run`)

- Track `lastTestOutput` (captured from any `runTests` tool result) and
  `critiqueRounds`.
- At the completion branch, if `mode == CRITIC`, `critiqueRounds <
  MAX_CRITIQUE_ROUNDS` (= 2), and `editJournal` is non-empty:
  review; on REVISE, increment the counter, append a feedback `UserMessage`,
  and `continue`.
- The cap prevents critic↔repair ping-pong. An empty journal (the agent only
  answered a question, changed nothing) skips the critic entirely.
- New 8-arg constructor; the existing 4-/5-arg constructors delegate with
  `Critic.noop()`, `CritiqueMode.NONE`, a fresh `EditJournal` — so existing
  callers and tests are unaffected.

`Tracer` gains `critique(Critique)`; the console tracer prints
`[critic] APPROVE` / `[critic] REVISE: <feedback>`.

## 6. Configuration

| Env var | Default | Meaning |
|---------|---------|---------|
| `AGENT_CRITIQUE` | `none` | `none` \| `self` \| `critic` |
| `AGENT_CRITIC_MODEL` | = `AGENT_MODEL` | model the critic runs (can differ for an independent perspective) |

`AgentConfig`'s shape is intentionally **unchanged**; critique wiring lives in
`App.buildCritic(...)` so the config record and its tests stay stable.

Run with the critic:
```
$env:AGENT_CRITIQUE = "critic"
./gradlew :agent-cli:run --args="<absolute-project-path> Fix the failing tests"
```

## 7. Verification

- **Live discrimination** (`LlmCriticLiveCheck`, `@Disabled` by default): against
  real `qwen2.5:7b`, the critic REVISES a fix that dropped `package` (tests red)
  and APPROVES a correct fix (tests green). Confirmed:
  `[BAD ] approved=false … provide a complete, compilable file` / `[GOOD] approved=true`.
- **Loop logic** (`AgentCritiqueTest`, mocked): reject-then-approve runs an extra
  round; rounds are capped at 2; `NONE` never calls the critic; an empty journal
  skips it.
- **Parser** (`LlmCriticParseTest`): verdict parsing, case-insensitivity, and
  fail-open behaviour.

## 8. How it becomes an ablation row (next)

`AblationRunner` will run `EvalHarness` under each `CritiqueMode` and report:

```
AGENT_CRITIQUE   resolution rate   avg iters   avg tokens
none             …%                …           …
self             …%                …           …
critic           …%                …           …   (extra tokens; independent review)
```

The objective, program-based `EvalHarness` quantifies whether the separate
critic actually beats self-critique — closing the harness-engineering loop:
mechanism → implementation → measured value.

## 9. Known trade-offs

1. Extra model calls (1–2 per task); bounded by the completion gate +
   `MAX_CRITIQUE_ROUNDS`.
2. The critic itself can err (false reject / false approve); fail-open keeps it
   from blocking, and the real test oracle remains the final word.
3. `SELF` mode is defined but not yet implemented (behaves as `NONE`).
