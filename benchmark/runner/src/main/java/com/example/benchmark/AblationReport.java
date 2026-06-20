package com.example.benchmark;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregates the per-cell {@link AblationStats} into a cross-config comparison:
 * a headline matrix (mean resolution rate ± spread, false positives, cost), a
 * per-case stability grid (how many of K runs each case was resolved), and a
 * single CSV carrying every raw run.
 */
public final class AblationReport {

    private final List<AblationStats> cells;

    public AblationReport(List<AblationStats> cells) {
        this.cells = List.copyOf(cells);
    }

    public List<AblationStats> cells() {
        return cells;
    }

    /** Headline matrix, one row per config. */
    public String matrixTable() {
        int repeats = cells.isEmpty() ? 0 : cells.get(0).repeats();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Ablation matrix (RAG x memory x self-critique), ")
          .append(repeats).append(" run(s)/cell ===\n");
        sb.append(String.format("%-12s %16s %10s %12s%n",
                "config", "resolve_rate", "false_pos", "wall(s)"));
        for (AblationStats c : cells) {
            String rate = String.format("%.1f%% ±%4.1f",
                    c.meanResolutionRate() * 100, c.stdDevResolutionRate() * 100);
            sb.append(String.format("%-12s %16s %10.1f %12.1f%n",
                    c.label(), rate, c.meanFalsePositives(), c.meanWallSeconds()));
        }
        return sb.toString().stripTrailing();
    }

    /** Per-case grid: cell = how many of K runs resolved that case (k/K). */
    public String perCaseGrid() {
        Set<String> caseIds = new LinkedHashSet<>();
        for (AblationStats c : cells) {
            caseIds.addAll(c.caseIds());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Per-case stability (resolved runs / total runs) ===\n");
        sb.append(String.format("%-14s", "case"));
        for (AblationStats c : cells) {
            sb.append(String.format(" %-11s", c.label()));
        }
        sb.append('\n');

        for (String id : caseIds) {
            sb.append(String.format("%-14s", id));
            for (AblationStats c : cells) {
                String cell = c.caseIds().contains(id)
                        ? c.timesResolved(id) + "/" + c.repeats()
                        : "-";
                sb.append(String.format(" %-11s", cell));
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Every raw run under one CSV header, with a {@code run} index column. */
    public String toCsv() {
        StringBuilder sb = new StringBuilder(
                "config,run,bug_id,resolved,completed,iterations,wall_clock_ms,stop_reason\n");
        for (AblationStats c : cells) {
            int run = 0;
            for (EvalReport r : c.runs()) {
                run++;
                for (EvalResult x : r.results()) {
                    sb.append(c.label()).append(',')
                      .append(run).append(',')
                      .append(x.id()).append(',')
                      .append(x.resolved()).append(',')
                      .append(x.completed()).append(',')
                      .append(x.iterations()).append(',')
                      .append(x.wallMillis()).append(',')
                      .append(x.stopReason()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
