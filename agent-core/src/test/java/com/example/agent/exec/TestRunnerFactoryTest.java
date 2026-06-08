package com.example.agent.exec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRunnerFactoryTest {

    @Test
    void selectsGradleWhenBuildGradleKtsPresent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("build.gradle.kts"), "");
        assertThat(TestRunner.forProject(root)).isInstanceOf(GradleTestRunner.class);
    }

    @Test
    void selectsGradleWhenLegacyBuildGradlePresent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("build.gradle"), "");
        assertThat(TestRunner.forProject(root)).isInstanceOf(GradleTestRunner.class);
    }

    @Test
    void selectsGradleWhenOnlySettingsKtsPresent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("settings.gradle.kts"), "");
        assertThat(TestRunner.forProject(root)).isInstanceOf(GradleTestRunner.class);
    }

    @Test
    void selectsMavenWhenPomPresent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        assertThat(TestRunner.forProject(root)).isInstanceOf(MavenTestRunner.class);
    }

    @Test
    void prefersGradleWhenBothBuildFilesPresent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("build.gradle.kts"), "");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        assertThat(TestRunner.forProject(root)).isInstanceOf(GradleTestRunner.class);
    }

    @Test
    void throwsWhenNoBuildFilePresent(@TempDir Path root) {
        assertThatThrownBy(() -> TestRunner.forProject(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No supported build file");
    }
}
