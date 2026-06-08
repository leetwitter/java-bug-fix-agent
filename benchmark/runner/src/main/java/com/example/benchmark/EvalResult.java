package com.example.benchmark;

/**
 * The outcome of evaluating one benchmark case.
 *
 * @param id          the case id
 * @param resolved    whether the project's tests pass after the agent ran
 *                    (graded independently by the harness — the oracle)
 * @param completed   whether the agent loop itself reported COMPLETED
 *                    (may be true while {@code resolved} is false — a false positive)
 * @param iterations  agent loop iterations used
 * @param wallMillis  wall-clock time for the whole case (run + grade)
 * @param stopReason  the agent's stop reason, or an EXCEPTION:* marker
 */
public record EvalResult(
        String id,
        boolean resolved,
        boolean completed,
        int iterations,
        long wallMillis,
        String stopReason) {
}
