package com.example.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkCatalogTest {

    @Test
    void discoversProjectDirsAndIgnoresNonProjects(@TempDir Path projects) throws IOException {
        Path calc01 = projects.resolve("calc01");
        Files.createDirectories(calc01);
        Files.writeString(calc01.resolve("settings.gradle.kts"), "");

        Path mavenBug = projects.resolve("str02");
        Files.createDirectories(mavenBug);
        Files.writeString(mavenBug.resolve("pom.xml"), "<project/>");

        Path notAProject = projects.resolve("notes");
        Files.createDirectories(notAProject);
        Files.writeString(notAProject.resolve("README.md"), "hi");

        List<BenchmarkCase> cases = BenchmarkCatalog.discover(projects, "fix it");

        assertThat(cases).extracting(BenchmarkCase::id)
                .containsExactly("calc01", "str02");  // sorted, non-project excluded
        assertThat(cases).allMatch(c -> c.prompt().equals("fix it"));
    }

    @Test
    void throwsWhenProjectsDirMissing(@TempDir Path tmp) {
        assertThatThrownBy(() -> BenchmarkCatalog.discover(tmp.resolve("nope"), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
