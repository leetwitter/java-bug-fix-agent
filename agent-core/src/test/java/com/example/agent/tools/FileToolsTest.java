package com.example.agent.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileToolsTest {

    // --- readFile ----------------------------------------------------------

    @Test
    void readsExistingFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("hello.txt"), "hi there");
        FileTools tools = new FileTools(root);

        assertThat(tools.readFile("hello.txt")).isEqualTo("hi there");
    }

    @Test
    void returnsErrorForMissingFile(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        assertThat(tools.readFile("nope.txt")).startsWith("Error:");
    }

    @Test
    void rejectsPathEscapingProjectRoot(@TempDir Path root) throws IOException {
        Path outside = root.getParent().resolve("outside-secret.txt");
        Files.writeString(outside, "secret");
        FileTools tools = new FileTools(root);

        String result = tools.readFile("../" + outside.getFileName());

        assertThat(result).startsWith("Error:").contains("escapes");
        Files.deleteIfExists(outside);
    }

    @Test
    void rejectsBlankPath(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        assertThat(tools.readFile("")).startsWith("Error:");
        assertThat(tools.readFile("   ")).startsWith("Error:");
    }

    @Test
    void rejectsDirectoryPath(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("subdir"));
        FileTools tools = new FileTools(root);

        assertThat(tools.readFile("subdir")).startsWith("Error:").contains("not a regular file");
    }

    // --- writeFile ---------------------------------------------------------

    @Test
    void writeFileOverwritesExistingFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "old");
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("a.txt", "new content");

        assertThat(result).startsWith("OK:");
        assertThat(Files.readString(root.resolve("a.txt"))).isEqualTo("new content");
    }

    @Test
    void writeFileRefusesTestPaths(@TempDir Path root) throws IOException {
        Path testDir = root.resolve("src/test/java");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("FooTest.java"), "// original");
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("src/test/java/FooTest.java", "// tampered");

        assertThat(result).startsWith("Error:").contains("test paths are forbidden");
        assertThat(Files.readString(testDir.resolve("FooTest.java"))).isEqualTo("// original");
    }

    @Test
    void writeFileRefusesNestedTestPaths(@TempDir Path root) throws IOException {
        Path testDir = root.resolve("module-a/src/test/java");
        Files.createDirectories(testDir);
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("module-a/src/test/java/Foo.java", "x");

        assertThat(result).startsWith("Error:").contains("test paths");
    }

    @Test
    void writeFileRefusesPathEscape(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("../outside.txt", "boom");

        assertThat(result).startsWith("Error:").contains("escapes");
    }

    @Test
    void writeFileRefusesNullContent(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "old");
        FileTools tools = new FileTools(root);

        assertThat(tools.writeFile("a.txt", null)).startsWith("Error:");
        assertThat(Files.readString(root.resolve("a.txt"))).isEqualTo("old");
    }

    @Test
    void writeFileRefusesMissingParentDir(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("does/not/exist/x.txt", "hi");

        assertThat(result).startsWith("Error:").contains("parent directory");
    }

    @Test
    void writeFileCreatesNewFileWhenParentExists(@TempDir Path root) throws IOException {
        FileTools tools = new FileTools(root);

        String result = tools.writeFile("new.txt", "fresh");

        assertThat(result).startsWith("OK:");
        assertThat(Files.readString(root.resolve("new.txt"))).isEqualTo("fresh");
    }

    // --- listFiles ---------------------------------------------------------

    @Test
    void listFilesShowsDirectoriesFirst(@TempDir Path root) throws IOException {
        Files.createDirectory(root.resolve("zdir"));
        Files.writeString(root.resolve("a.txt"), "a");
        Files.writeString(root.resolve("m.txt"), "m");
        FileTools tools = new FileTools(root);

        String result = tools.listFiles("");

        String[] lines = result.split("\n");
        assertThat(lines[0]).startsWith("[D] zdir");
        assertThat(lines[1]).startsWith("[F] a.txt");
        assertThat(lines[2]).startsWith("[F] m.txt");
    }

    @Test
    void listFilesAcceptsDotForRoot(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("only.txt"), "x");
        FileTools tools = new FileTools(root);

        String result = tools.listFiles(".");

        assertThat(result).contains("[F] only.txt");
    }

    @Test
    void listFilesReturnsErrorForMissingDir(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        assertThat(tools.listFiles("nowhere")).startsWith("Error:").contains("not found");
    }

    @Test
    void listFilesReturnsErrorForRegularFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "x");
        FileTools tools = new FileTools(root);

        assertThat(tools.listFiles("a.txt")).startsWith("Error:").contains("not a directory");
    }

    @Test
    void listFilesRejectsPathEscape(@TempDir Path root) {
        FileTools tools = new FileTools(root);

        assertThat(tools.listFiles("..")).startsWith("Error:").contains("escapes");
    }

    @Test
    void listFilesIncludesByteSizes(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "1234");
        FileTools tools = new FileTools(root);

        String result = tools.listFiles("");

        assertThat(result).contains("a.txt").contains("4B");
    }
}
