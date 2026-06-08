package com.example.agent.exec;

import java.time.Duration;
import java.util.List;

/**
 * Outcome of a single test-suite invocation against a target project.
 *
 * @param passed         {@code true} iff the build's test task succeeded.
 * @param failingTests   fully-qualified "classname#methodname" identifiers of
 *                       failing tests, parsed from JUnit XML reports.
 *                       Empty when {@link #passed} is {@code true}, or when
 *                       parsing failed.
 * @param output         the combined stdout+stderr of the build invocation,
 *                       truncated to a sane cap.
 * @param exitCode       process exit code; {@code -1} on launch failure,
 *                       {@code -2} on timeout.
 * @param duration       wall-clock time spent in the runner.
 */
public record TestResult(
        boolean passed,
        List<String> failingTests,
        String output,
        int exitCode,
        Duration duration) {

    public TestResult {
        failingTests = List.copyOf(failingTests);
    }
}
