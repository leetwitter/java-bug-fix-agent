package com.example.benchmark;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Aggregates the K repeated runs of a single ablation cell into summary
 * statistics. A 7B local model is non-deterministic, so one run per cell is
 * noise; averaging over K runs (with the spread) is what makes the matrix
 * comparable.
 *
 * @param label the {@code AblationConfig} label (matrix row key)
 * @param runs  the K {@link EvalReport}s, one per repeat (K >= 1)
 */
public record AblationStats(String label, List<EvalReport> runs) {

    public AblationStats {
        if (runs == null || runs.isEmpty()) {
            throw new IllegalArgumentException("a cell needs at least one run");
        }
        runs = List.copyOf(runs);
    }

    public int repeats() {
        return runs.size();
    }

    /** Per-run resolution rates (one value per repeat). */
    public double[] resolutionRates() {
        return runs.stream().mapToDouble(EvalReport::resolutionRate).toArray();
    }

    public double meanResolutionRate() {
        return runs.stream().mapToDouble(EvalReport::resolutionRate).average().orElse(0.0);
    }

    /** Sample standard deviation of the resolution rate; 0 when only one run. */
    public double stdDevResolutionRate() {
        int n = runs.size();
        if (n < 2) return 0.0;
        double mean = meanResolutionRate();
        double sumSq = 0.0;
        for (double r : resolutionRates()) {
            double d = r - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    private static long falsePositives(EvalReport r) {
        return r.results().stream().filter(x -> x.completed() && !x.resolved()).count();
    }

    public double meanFalsePositives() {
        return runs.stream().mapToLong(AblationStats::falsePositives).average().orElse(0.0);
    }

    public double meanWallSeconds() {
        return runs.stream()
                .mapToLong(r -> r.results().stream().mapToLong(EvalResult::wallMillis).sum())
                .average().orElse(0.0) / 1000.0;
    }

    /** All case ids seen across the runs, in first-seen order. */
    public Set<String> caseIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (EvalReport r : runs) {
            r.results().forEach(x -> ids.add(x.id()));
        }
        return ids;
    }

    /** How many of the K runs resolved {@code caseId}. */
    public int timesResolved(String caseId) {
        int count = 0;
        for (EvalReport r : runs) {
            boolean resolved = r.results().stream()
                    .anyMatch(x -> x.id().equals(caseId) && x.resolved());
            if (resolved) count++;
        }
        return count;
    }
}
