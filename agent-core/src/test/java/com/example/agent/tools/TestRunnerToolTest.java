package com.example.agent.tools;

import com.example.agent.exec.TestResult;
import com.example.agent.exec.TestRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestRunnerToolTest {

    @Test
    void formatsPassedResult() {
        TestRunner runner = mock(TestRunner.class);
        when(runner.runTests()).thenReturn(
                new TestResult(true, List.of(), "BUILD SUCCESSFUL", 0, Duration.ofMillis(250)));

        String out = new TestRunnerTool(runner).runTests();

        assertThat(out)
                .startsWith("PASSED")
                .contains("exit=0")
                .contains("duration=250ms")
                .contains("BUILD SUCCESSFUL")
                .doesNotContain("Failing tests");
    }

    @Test
    void formatsFailedResultWithListOfFailingTests() {
        TestRunner runner = mock(TestRunner.class);
        when(runner.runTests()).thenReturn(new TestResult(
                false,
                List.of("com.example.FooTest#bar", "com.example.FooTest#baz"),
                "FAILURE: tests failed",
                1,
                Duration.ofMillis(420)));

        String out = new TestRunnerTool(runner).runTests();

        assertThat(out)
                .startsWith("FAILED")
                .contains("exit=1")
                .contains("Failing tests:")
                .contains("com.example.FooTest#bar")
                .contains("com.example.FooTest#baz")
                .contains("--- build output (tail) ---")
                .contains("FAILURE");
    }

    @Test
    void truncatesLongOutput() {
        TestRunner runner = mock(TestRunner.class);
        // Use '@' as the marker — it doesn't appear in the tool's formatting prefix
        // ("FAILED (exit=…)", "Failing tests:", "--- build output (tail) ---").
        String big = "@".repeat(20_000);
        when(runner.runTests()).thenReturn(
                new TestResult(false, List.of("a#b"), big, 1, Duration.ofMillis(1)));

        String out = new TestRunnerTool(runner).runTests();

        int markerCount = (int) out.chars().filter(ch -> ch == '@').count();
        assertThat(markerCount).isLessThan(big.length()).isLessThanOrEqualTo(6_000);
    }
}
