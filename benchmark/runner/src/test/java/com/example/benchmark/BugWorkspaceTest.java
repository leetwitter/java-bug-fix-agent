package com.example.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BugWorkspaceTest {

    @Test
    void copiesSourcesButSkipsBuildArtefactDirs(@TempDir Path template) throws IOException {
        Files.writeString(template.resolve("build.gradle.kts"), "// build");
        Files.createDirectories(template.resolve("src/main/java"));
        Files.writeString(template.resolve("src/main/java/A.java"), "class A {}");
        Files.createDirectories(template.resolve("build/classes"));
        Files.writeString(template.resolve("build/classes/A.class"), "stale");
        Files.createDirectories(template.resolve(".gradle"));
        Files.writeString(template.resolve(".gradle/cache"), "stale");

        Path workdir;
        try (BugWorkspace ws = BugWorkspace.copyOf(template)) {
            workdir = ws.dir();
            assertThat(workdir).isNotEqualTo(template);
            assertThat(ws.dir().resolve("build.gradle.kts")).exists();
            assertThat(ws.dir().resolve("src/main/java/A.java")).exists();
            assertThat(ws.dir().resolve("build")).doesNotExist();
            assertThat(ws.dir().resolve(".gradle")).doesNotExist();
        }
        // template untouched, workspace cleaned up on close
        assertThat(template.resolve("src/main/java/A.java")).exists();
        assertThat(workdir).doesNotExist();
    }

    @Test
    void skipsNestedBuildDirs() {
        assertThat(BugWorkspace.SKIP_DIRS).contains("build", ".gradle", ".git");
    }
}
