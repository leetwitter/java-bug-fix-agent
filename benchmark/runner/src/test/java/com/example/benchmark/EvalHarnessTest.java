package com.example.benchmark;

import com.example.agent.core.RunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalHarnessTest {

    private static BenchmarkCase seedCase(Path template, String id) throws IOException {
        Path dir = template.resolve(id);
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(dir.resolve("build.gradle.kts"), "// build");
        Files.writeString(dir.resolve("src/main/java/A.java"), "class A {}");
        return new BenchmarkCase(id, dir, "fix it");
    }

    @Test
    void runsAgentOnACopyAndGradesIndependently(@TempDir Path template) throws IOException {
        BenchmarkCase bug = seedCase(template, "calc01");
        List<Path> workdirsSeen = new ArrayList<>();

        EvalHarness.AgentRunner runner = workdir -> {
            workdirsSeen.add(workdir);
            return RunResult.completed("done", 4);
        };
        // Agent claims COMPLETED, but the grader is the real oracle and says FAIL.
        EvalHarness.Grader grader = workdir -> false;

        EvalReport report = new EvalHarness("NONE", runner, grader, EvalHarness.ProgressListener.NONE)
                .evaluate(List.of(bug));

        EvalResult r = report.results().get(0);
        assertThat(r.id()).isEqualTo("calc01");
        assertThat(r.completed()).isTrue();    // agent said done
        assertThat(r.resolved()).isFalse();    // ...but tests still fail -> false positive caught
        assertThat(r.iterations()).isEqualTo(4);

        // The agent ran on a temp copy, never the template; the template survives.
        assertThat(workdirsSeen).hasSize(1);
        assertThat(workdirsSeen.get(0)).isNotEqualTo(bug.templateDir());
        assertThat(workdirsSeen.get(0)).doesNotExist();      // workspace cleaned up
        assertThat(bug.templateDir().resolve("src/main/java/A.java")).exists();
    }

    @Test
    void resolvedWhenGraderPasses(@TempDir Path template) throws IOException {
        BenchmarkCase bug = seedCase(template, "calc01");

        EvalReport report = new EvalHarness(
                "CRITIC",
                workdir -> RunResult.completed("fixed", 6),
                workdir -> true,
                EvalHarness.ProgressListener.NONE).evaluate(List.of(bug));

        assertThat(report.resolvedCount()).isEqualTo(1);
        assertThat(report.results().get(0).resolved()).isTrue();
    }

    @Test
    void agentExceptionIsRecordedNotPropagated(@TempDir Path template) throws IOException {
        BenchmarkCase bug = seedCase(template, "boom");

        EvalReport report = new EvalHarness(
                "NONE",
                workdir -> { throw new RuntimeException("model down"); },
                workdir -> false,
                EvalHarness.ProgressListener.NONE).evaluate(List.of(bug));

        EvalResult r = report.results().get(0);
        assertThat(r.resolved()).isFalse();
        assertThat(r.completed()).isFalse();
        assertThat(r.stopReason()).startsWith("EXCEPTION:");
    }
}
