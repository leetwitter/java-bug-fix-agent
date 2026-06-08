package com.example.agent.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

abstract class ProcessTestRunner implements TestRunner {

    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    static final int MAX_OUTPUT_CHARS = 50_000;

    protected final Path projectRoot;
    private final Duration timeout;

    ProcessTestRunner(Path projectRoot) {
        this(projectRoot, DEFAULT_TIMEOUT);
    }

    ProcessTestRunner(Path projectRoot, Duration timeout) {
        this.projectRoot = projectRoot;
        this.timeout = timeout;
    }

    @Override
    public final TestResult runTests() {
        Instant start = Instant.now();
        ProcessOutput po;
        try {
            po = runProcess(testCommand());
        } catch (IOException e) {
            return new TestResult(false, List.of(),
                    "Error launching tests: " + e.getMessage(),
                    -1, Duration.between(start, Instant.now()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestResult(false, List.of(),
                    "Test runner interrupted.",
                    -1, Duration.between(start, Instant.now()));
        }
        Duration duration = Duration.between(start, Instant.now());
        boolean passed = po.exitCode == 0;
        List<String> failing = passed
                ? List.of()
                : JUnitXmlParser.findFailingTests(projectRoot, reportSubdirs());
        return new TestResult(passed, failing, truncate(po.output), po.exitCode, duration);
    }

    /** Build the OS-level command to invoke. */
    protected abstract List<String> testCommand();

    /** Subdirectories (relative to project root) where JUnit XML reports land. */
    protected abstract List<String> reportSubdirs();

    private ProcessOutput runProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder buf = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            char[] chunk = new char[4096];
            int n;
            while ((n = reader.read(chunk)) != -1) {
                buf.append(chunk, 0, n);
                if (buf.length() > MAX_OUTPUT_CHARS * 2) {
                    // Keep reading to drain the pipe (so the process can exit),
                    // but stop accumulating to avoid OOM.
                    while (reader.read(chunk) != -1) { /* drain */ }
                    break;
                }
            }
        }
        boolean finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            proc.destroyForcibly();
            buf.append("\n[TIMEOUT after ").append(timeout).append("]");
            return new ProcessOutput(-2, buf.toString());
        }
        return new ProcessOutput(proc.exitValue(), buf.toString());
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_OUTPUT_CHARS) return s;
        return s.substring(0, MAX_OUTPUT_CHARS) + "\n…[truncated]";
    }

    private record ProcessOutput(int exitCode, String output) {}
}
