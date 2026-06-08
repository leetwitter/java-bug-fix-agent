package com.example.agent.exec;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the test suite of a *target* project (the one the agent is fixing).
 * The agent's own project always uses Gradle; targets in the benchmark may
 * be either Gradle or Maven, so the factory auto-detects.
 */
public interface TestRunner {

    TestResult runTests();

    static TestRunner forProject(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("build.gradle.kts"))
                || Files.exists(projectRoot.resolve("build.gradle"))
                || Files.exists(projectRoot.resolve("settings.gradle.kts"))
                || Files.exists(projectRoot.resolve("settings.gradle"))) {
            return new GradleTestRunner(projectRoot);
        }
        if (Files.exists(projectRoot.resolve("pom.xml"))) {
            return new MavenTestRunner(projectRoot);
        }
        throw new IllegalArgumentException(
                "No supported build file (build.gradle{,.kts} or pom.xml) at " + projectRoot);
    }
}
