package com.example.benchmark;

import com.example.agent.assembly.BugfixAgentFactory;
import com.example.agent.assembly.BugfixAgentFactory.Assembled;
import com.example.agent.core.AgentConfig;
import com.example.agent.core.Task;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.exec.TestRunner;
import com.example.agent.observability.Tracer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Entry point for {@code ./gradlew :benchmark:runner:runEval}. Discovers the
 * seeded-bug benchmark, runs the agent over every case under the configured
 * critique mode, prints a metrics summary, and writes a CSV under reports/.
 *
 * <p>Honours the same env vars as the CLI (AGENT_MODEL, AGENT_CRITIQUE, …) so a
 * before/after comparison is just two runs with a different AGENT_CRITIQUE.
 */
public final class EvalRunner {

    private EvalRunner() {}

    public static void main(String[] args) {
        Path root = Path.of(System.getProperty("benchmark.root", ".")).toAbsolutePath().normalize();
        Path projectsDir = root.resolve("benchmark").resolve("projects");

        AgentConfig config = AgentConfig.fromEnv();
        CritiqueMode mode = CritiqueMode.fromString(System.getenv("AGENT_CRITIQUE"));
        String criticModel = System.getenv("AGENT_CRITIC_MODEL");

        List<BenchmarkCase> cases =
                BenchmarkCatalog.discover(projectsDir, BugfixAgentFactory.DEFAULT_BUGFIX_PROMPT);

        System.out.println("[eval] provider=" + config.provider()
                + " model=" + config.modelName()
                + " critique=" + mode
                + " cases=" + cases.size()
                + " from " + projectsDir);

        boolean trace = Boolean.parseBoolean(System.getenv("BENCHMARK_TRACE"));
        EvalHarness.AgentRunner runner = workdir -> {
            Tracer tracer = trace ? Tracer.console() : Tracer.noop();
            Assembled assembled =
                    BugfixAgentFactory.assemble(workdir, config, mode, criticModel, tracer);
            return assembled.agent().run(new Task(BugfixAgentFactory.DEFAULT_BUGFIX_PROMPT, workdir));
        };
        EvalHarness.Grader grader = workdir -> TestRunner.forProject(workdir).runTests().passed();
        EvalHarness.ProgressListener progress =
                (id, phase) -> System.out.println("[eval] " + id + " :: " + phase);

        EvalReport report = new EvalHarness(mode.name(), runner, grader, progress).evaluate(cases);

        System.out.println();
        System.out.println(report.summary());
        writeCsv(root, report);
    }

    private static void writeCsv(Path root, EvalReport report) {
        try {
            Path reportsDir = root.resolve("reports");
            Files.createDirectories(reportsDir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path csv = reportsDir.resolve("eval-" + report.label() + "-" + ts + ".csv");
            Files.writeString(csv, report.toCsv());
            System.out.println();
            System.out.println("[eval] wrote " + csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
