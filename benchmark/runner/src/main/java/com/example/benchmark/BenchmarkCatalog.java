package com.example.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers benchmark cases by scanning a projects directory: every immediate
 * subdirectory that looks like a Gradle or Maven project becomes one case.
 * Adding a bug to the benchmark is therefore just dropping in a seeded project.
 */
public final class BenchmarkCatalog {

    private BenchmarkCatalog() {}

    public static List<BenchmarkCase> discover(Path projectsDir, String prompt) {
        if (!Files.isDirectory(projectsDir)) {
            throw new IllegalArgumentException("benchmark projects dir not found: " + projectsDir);
        }
        List<BenchmarkCase> cases = new ArrayList<>();
        try (Stream<Path> entries = Files.list(projectsDir)) {
            entries.filter(Files::isDirectory)
                    .filter(BenchmarkCatalog::isProject)
                    .sorted()
                    .forEach(dir -> cases.add(
                            new BenchmarkCase(dir.getFileName().toString(), dir, prompt)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cases;
    }

    static boolean isProject(Path dir) {
        return Files.exists(dir.resolve("settings.gradle.kts"))
                || Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("build.gradle.kts"))
                || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("pom.xml"));
    }
}
