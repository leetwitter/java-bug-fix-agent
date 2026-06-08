package com.example.benchmark;

import com.example.agent.core.RunResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the agent across a set of {@link BenchmarkCase}s and grades each one.
 *
 * <p>The agent run and the grading are injected as {@link AgentRunner} and
 * {@link Grader} so the orchestration (copy → run → grade → record → clean up)
 * is unit-testable without a live model or a real build. In production
 * {@code EvalRunner} supplies a real agent and a {@code TestRunner}-backed
 * grader.
 *
 * <p>Grading is independent of what the agent <em>thinks</em>: {@code resolved}
 * is decided purely by whether the project's tests pass, never by the agent's
 * own COMPLETED claim.
 */
public final class EvalHarness {

    @FunctionalInterface
    public interface AgentRunner {
        RunResult run(Path workspaceDir);
    }

    @FunctionalInterface
    public interface Grader {
        boolean passes(Path workspaceDir);
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onCase(String id, String phase);

        ProgressListener NONE = (id, phase) -> { };
    }

    private final String label;
    private final AgentRunner agentRunner;
    private final Grader grader;
    private final ProgressListener progress;

    public EvalHarness(String label, AgentRunner agentRunner, Grader grader, ProgressListener progress) {
        this.label = label;
        this.agentRunner = agentRunner;
        this.grader = grader;
        this.progress = progress == null ? ProgressListener.NONE : progress;
    }

    public EvalReport evaluate(List<BenchmarkCase> cases) {
        List<EvalResult> results = new ArrayList<>();
        for (BenchmarkCase bug : cases) {
            results.add(evaluateOne(bug));
        }
        return new EvalReport(label, results);
    }

    private EvalResult evaluateOne(BenchmarkCase bug) {
        progress.onCase(bug.id(), "copy");
        try (BugWorkspace workspace = BugWorkspace.copyOf(bug.templateDir())) {
            long t0 = System.currentTimeMillis();

            int iterations = 0;
            boolean completed = false;
            String stopReason;
            progress.onCase(bug.id(), "run");
            try {
                RunResult rr = agentRunner.run(workspace.dir());
                iterations = rr.iterations();
                completed = rr.completed();
                stopReason = rr.stopReason().name();
            } catch (RuntimeException e) {
                stopReason = "EXCEPTION:" + e.getClass().getSimpleName();
            }

            progress.onCase(bug.id(), "grade");
            boolean resolved = safeGrade(workspace.dir());

            long wall = System.currentTimeMillis() - t0;
            progress.onCase(bug.id(), resolved ? "RESOLVED" : "UNRESOLVED");
            return new EvalResult(bug.id(), resolved, completed, iterations, wall, stopReason);
        }
    }

    private boolean safeGrade(Path dir) {
        try {
            return grader.passes(dir);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
