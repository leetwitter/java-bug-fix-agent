package com.example.agent.tools;

import com.example.agent.exec.TestResult;
import com.example.agent.exec.TestRunner;
import dev.langchain4j.agent.tool.Tool;

import java.util.Objects;

/**
 * Wraps a {@link TestRunner} as a LangChain4j {@code @Tool} the agent can
 * call. The agent gets back a compact, structured summary it can act on.
 */
public class TestRunnerTool {

    private static final int OUTPUT_TAIL_CHARS = 6_000;

    private final TestRunner runner;

    public TestRunnerTool(TestRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Tool("Run the target project's test suite and return the result. " +
          "Takes no arguments. " +
          "Returns a structured report starting with either 'PASSED' or 'FAILED'; " +
          "when FAILED, the report lists the fully-qualified failing tests (classname#methodname) " +
          "and the tail of the build output. " +
          "Call this after editing source files to verify a fix.")
    public String runTests() {
        TestResult r = runner.runTests();
        StringBuilder out = new StringBuilder();
        out.append(r.passed() ? "PASSED" : "FAILED")
                .append(" (exit=").append(r.exitCode())
                .append(", duration=").append(r.duration().toMillis()).append("ms)\n");

        if (!r.failingTests().isEmpty()) {
            out.append("\nFailing tests:\n");
            for (String t : r.failingTests()) {
                out.append("  - ").append(t).append('\n');
            }
        }

        String output = r.output() == null ? "" : r.output();
        if (!output.isEmpty()) {
            out.append("\n--- build output (tail) ---\n");
            out.append(output.length() <= OUTPUT_TAIL_CHARS
                    ? output
                    : output.substring(output.length() - OUTPUT_TAIL_CHARS));
        }
        return out.toString();
    }
}
