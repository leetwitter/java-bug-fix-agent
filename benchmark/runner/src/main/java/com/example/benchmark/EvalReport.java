package com.example.benchmark;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Aggregates {@link EvalResult}s into a headline resolution rate plus a
 * human-readable summary and a machine-readable CSV. The CSV's leading
 * {@code config} column carries the run label (e.g. the critique mode), so
 * several runs can be concatenated into one ablation table later.
 */
public final class EvalReport {

    private final String label;
    private final List<EvalResult> results;

    public EvalReport(String label, List<EvalResult> results) {
        this.label = label;
        this.results = List.copyOf(results);
    }

    public String label() {
        return label;
    }

    public List<EvalResult> results() {
        return results;
    }

    public int total() {
        return results.size();
    }

    public long resolvedCount() {
        return results.stream().filter(EvalResult::resolved).count();
    }

    public double resolutionRate() {
        return results.isEmpty() ? 0.0 : (double) resolvedCount() / results.size();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Benchmark summary (config=").append(label).append(") ===\n");
        sb.append(String.format("cases:    %d%n", total()));
        sb.append(String.format("resolved: %d/%d (%.1f%%)%n",
                resolvedCount(), total(), resolutionRate() * 100));

        OptionalDouble avgIters = results.stream()
                .filter(EvalResult::resolved)
                .mapToInt(EvalResult::iterations)
                .average();
        sb.append(String.format("avg iterations (resolved): %s%n",
                avgIters.isPresent() ? String.format("%.1f", avgIters.getAsDouble()) : "n/a"));

        long totalWall = results.stream().mapToLong(EvalResult::wallMillis).sum();
        sb.append(String.format("total wall-clock: %.1fs%n", totalWall / 1000.0));

        sb.append("per-case:\n");
        for (EvalResult r : results) {
            sb.append(String.format("  %-14s %-11s iters=%-3d wall=%6.1fs stop=%s%n",
                    r.id(),
                    r.resolved() ? "RESOLVED" : "UNRESOLVED",
                    r.iterations(),
                    r.wallMillis() / 1000.0,
                    r.stopReason()));
        }
        return sb.toString().stripTrailing();
    }

    public String toCsv() {
        StringBuilder sb = new StringBuilder(
                "config,bug_id,resolved,completed,iterations,wall_clock_ms,stop_reason\n");
        for (EvalResult r : results) {
            sb.append(label).append(',')
              .append(r.id()).append(',')
              .append(r.resolved()).append(',')
              .append(r.completed()).append(',')
              .append(r.iterations()).append(',')
              .append(r.wallMillis()).append(',')
              .append(r.stopReason()).append('\n');
        }
        return sb.toString();
    }
}
