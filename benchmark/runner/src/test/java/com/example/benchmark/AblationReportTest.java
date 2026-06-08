package com.example.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AblationReportTest {

    private static EvalResult resolved(String id, int iters) {
        return new EvalResult(id, true, true, iters, 1_000L, "COMPLETED");
    }

    private static EvalResult falsePositive(String id) {
        // agent claimed COMPLETED but the oracle says the tests still fail
        return new EvalResult(id, false, true, 5, 2_000L, "COMPLETED");
    }

    private static EvalResult honestMiss(String id) {
        return new EvalResult(id, false, false, 8, 3_000L, "ERROR");
    }

    /** A cell whose 2 repeats resolve str02 both times, gcd03 once, calc01 never. */
    private static AblationStats flakyCell(String label) {
        EvalReport run1 = new EvalReport(label, List.of(
                resolved("str02", 5), resolved("gcd03", 4), honestMiss("calc01")));
        EvalReport run2 = new EvalReport(label, List.of(
                resolved("str02", 6), falsePositive("gcd03"), honestMiss("calc01")));
        return new AblationStats(label, List.of(run1, run2));
    }

    private static AblationStats baseCell() {
        EvalReport run1 = new EvalReport("BASE", List.of(
                falsePositive("str02"), falsePositive("gcd03"), falsePositive("calc01")));
        EvalReport run2 = new EvalReport("BASE", List.of(
                falsePositive("str02"), falsePositive("gcd03"), falsePositive("calc01")));
        return new AblationStats("BASE", List.of(run1, run2));
    }

    @Test
    void statsAverageResolutionRateAcrossRepeats() {
        AblationStats cell = flakyCell("RAG+CRITIC");
        // run1: 2/3 resolved, run2: 1/3 resolved -> mean 0.5
        assertThat(cell.meanResolutionRate()).isEqualTo(0.5);
        assertThat(cell.stdDevResolutionRate()).isGreaterThan(0.0);
        assertThat(cell.timesResolved("str02")).isEqualTo(2);
        assertThat(cell.timesResolved("gcd03")).isEqualTo(1);
        assertThat(cell.timesResolved("calc01")).isZero();
    }

    @Test
    void singleRunHasZeroStdDev() {
        AblationStats cell = new AblationStats("RAG",
                List.of(new EvalReport("RAG", List.of(resolved("str02", 5)))));
        assertThat(cell.stdDevResolutionRate()).isZero();
        assertThat(cell.repeats()).isEqualTo(1);
    }

    @Test
    void matrixTableShowsMeanRateAndFalsePositives() {
        AblationReport report = new AblationReport(List.of(baseCell(), flakyCell("RAG+CRITIC")));
        String table = report.matrixTable();

        assertThat(table).contains("config", "resolve_rate", "false_pos");
        assertThat(table).contains("2 run(s)/cell");
        assertThat(table).contains("BASE");
        assertThat(table).contains("RAG+CRITIC");
        // BASE resolves nothing
        assertThat(table).containsPattern("BASE\\s+0.0%");
        // RAG+CRITIC mean 50%
        assertThat(table).containsPattern("RAG\\+CRITIC\\s+50.0%");
    }

    @Test
    void perCaseGridShowsResolveFractions() {
        AblationReport report = new AblationReport(List.of(baseCell(), flakyCell("RAG+CRITIC")));
        String grid = report.perCaseGrid();

        assertThat(grid).contains("BASE", "RAG+CRITIC");
        // str02 resolved 0/2 under BASE, 2/2 under RAG+CRITIC
        assertThat(grid).containsPattern("str02\\s+0/2\\s+2/2");
        // gcd03 resolved 0/2 under BASE, 1/2 under RAG+CRITIC
        assertThat(grid).containsPattern("gcd03\\s+0/2\\s+1/2");
    }

    @Test
    void csvCarriesEveryRawRunWithRunIndex() {
        AblationReport report = new AblationReport(List.of(flakyCell("RAG+CRITIC")));
        String csv = report.toCsv();

        long headers = csv.lines().filter(l -> l.startsWith("config,run,bug_id")).count();
        assertThat(headers).isEqualTo(1);
        // 1 config x 2 runs x 3 cases = 6 data rows + 1 header
        assertThat(csv.lines().count()).isEqualTo(7);
        assertThat(csv).contains("RAG+CRITIC,1,str02,true,");
        assertThat(csv).contains("RAG+CRITIC,2,gcd03,false,true,");
    }
}
