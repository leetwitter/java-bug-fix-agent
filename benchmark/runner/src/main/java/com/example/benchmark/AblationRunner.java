package com.example.benchmark;

import com.example.agent.assembly.BugfixAgentFactory;
import com.example.agent.assembly.BugfixAgentFactory.Assembled;
import com.example.agent.core.AblationConfig;
import com.example.agent.core.AgentConfig;
import com.example.agent.core.Task;
import com.example.agent.exec.TestRunner;
import com.example.agent.observability.Tracer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for {@code ./gradlew :benchmark:runner:runAblation}. Runs the whole
 * seeded-bug benchmark once per cell of {@link AblationConfig#matrix()} (RAG on/off
 * x self-critique on/off), then prints a comparison matrix + per-case grid and
 * writes a single combined CSV under reports/.
 *
 * <p>Grading stays the independent test-oracle ({@link TestRunner}); the agent's
 * own COMPLETED claim only feeds the "false positive" column, never resolution.
 */
public final class AblationRunner {

    private AblationRunner() {}

    public static void main(String[] args) {
        Path root = Path.of(System.getProperty("benchmark.root", ".")).toAbsolutePath().normalize();
        Path projectsDir = root.resolve("benchmark").resolve("projects");

        AgentConfig config = AgentConfig.fromEnv();
        String criticModel = System.getenv("AGENT_CRITIC_MODEL");
        boolean trace = Boolean.parseBoolean(System.getenv("BENCHMARK_TRACE"));
        int repeats = repeatsFromEnv();

        List<BenchmarkCase> cases =
                BenchmarkCatalog.discover(projectsDir, BugfixAgentFactory.DEFAULT_BUGFIX_PROMPT);
        List<AblationConfig> matrix = AblationConfig.matrix();

        System.out.println("[ablation] provider=" + config.provider()
                + " model=" + config.modelName()
                + " cases=" + cases.size()
                + " configs=" + matrix.size()
                + " repeats=" + repeats
                + " (" + (cases.size() * matrix.size() * repeats) + " runs)"
                + " from " + projectsDir);

        EvalHarness.Grader grader = workdir -> TestRunner.forProject(workdir).runTests().passed();

        List<AblationStats> cells = new ArrayList<>();
        for (AblationConfig cell : matrix) {
            System.out.println();
            System.out.println("[ablation] === config " + cell.label()
                    + " (retrieval=" + cell.retrieval() + ", critique=" + cell.critique() + ") ===");

            EvalHarness.AgentRunner runner = workdir -> {
                Tracer tracer = trace ? Tracer.console() : Tracer.noop();
                Assembled assembled = BugfixAgentFactory.assemble(
                        workdir, config, cell.critique(), criticModel, tracer, cell.retrieval());
                return assembled.agent().run(new Task(BugfixAgentFactory.DEFAULT_BUGFIX_PROMPT, workdir));
            };

            List<EvalReport> runs = new ArrayList<>();
            for (int rep = 1; rep <= repeats; rep++) {
                int repNo = rep;
                EvalHarness.ProgressListener progress = (id, phase) ->
                        System.out.println("[ablation] " + cell.label() + " r" + repNo + " :: " + id + " :: " + phase);
                EvalReport report = new EvalHarness(cell.label(), runner, grader, progress).evaluate(cases);
                System.out.println("[ablation] " + cell.label() + " run " + repNo + "/" + repeats
                        + ": resolved " + report.resolvedCount() + "/" + report.total());
                runs.add(report);
            }
            cells.add(new AblationStats(cell.label(), runs));
        }

        AblationReport ablation = new AblationReport(cells);
        System.out.println();
        System.out.println(ablation.matrixTable());
        System.out.println();
        System.out.println(ablation.perCaseGrid());
        writeCsv(root, ablation);
    }

    /** Repeats per cell from AGENT_ABLATION_REPEATS (default 1, floored at 1). */
    private static int repeatsFromEnv() {
        String raw = System.getenv("AGENT_ABLATION_REPEATS");
        if (raw == null || raw.isBlank()) return 1;
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static void writeCsv(Path root, AblationReport report) {
        try {
            Path reportsDir = root.resolve("reports");
            Files.createDirectories(reportsDir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path csv = reportsDir.resolve("ablation-" + ts + ".csv");
            Files.writeString(csv, report.toCsv());
            System.out.println();
            System.out.println("[ablation] wrote " + csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
