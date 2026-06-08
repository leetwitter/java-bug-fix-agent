package com.example.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportTest {

    private static EvalResult res(String id, boolean resolved, boolean completed, int iters, String stop) {
        return new EvalResult(id, resolved, completed, iters, 1000L, stop);
    }

    @Test
    void computesResolutionRate() {
        EvalReport report = new EvalReport("CRITIC", List.of(
                res("a", true, true, 5, "COMPLETED"),
                res("b", false, true, 7, "COMPLETED"),   // false positive: completed but unresolved
                res("c", true, true, 3, "COMPLETED"),
                res("d", false, false, 10, "MAX_ITERATIONS")));

        assertThat(report.total()).isEqualTo(4);
        assertThat(report.resolvedCount()).isEqualTo(2);
        assertThat(report.resolutionRate()).isEqualTo(0.5);
    }

    @Test
    void csvHasConfigColumnAndOneRowPerCase() {
        EvalReport report = new EvalReport("NONE", List.of(
                res("calc01", true, true, 4, "COMPLETED")));

        String csv = report.toCsv();
        assertThat(csv).startsWith("config,bug_id,resolved,completed,iterations,wall_clock_ms,stop_reason\n");
        assertThat(csv).contains("NONE,calc01,true,true,4,1000,COMPLETED");
        assertThat(csv.strip().lines()).hasSize(2);  // header + one row
    }

    @Test
    void summaryMentionsRateAndEachCase() {
        EvalReport report = new EvalReport("CRITIC", List.of(
                res("calc01", true, true, 4, "COMPLETED"),
                res("str02", false, true, 9, "COMPLETED")));

        String summary = report.summary();
        assertThat(summary).contains("config=CRITIC");
        assertThat(summary).contains("resolved: 1/2 (50.0%)");
        assertThat(summary).contains("calc01").contains("RESOLVED");
        assertThat(summary).contains("str02").contains("UNRESOLVED");
    }

    @Test
    void handlesEmptyReport() {
        EvalReport report = new EvalReport("NONE", List.of());
        assertThat(report.resolutionRate()).isEqualTo(0.0);
        assertThat(report.summary()).contains("cases:    0");
    }
}
