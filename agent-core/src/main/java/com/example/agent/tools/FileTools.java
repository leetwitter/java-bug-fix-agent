package com.example.agent.tools;

import com.example.agent.core.EditJournal;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * File-system tools available to the agent. All paths are resolved relative
 * to a project root; anything that escapes the root is rejected. Writes to
 * test directories (Maven/Gradle convention {@code src/test/...}) are
 * blocked — the agent must fix production code, not the failing tests.
 */
public class FileTools {

    private static final int LIST_LIMIT = 200;
    private static final int MAX_WRITE_BYTES = 1_000_000;

    private final Path projectRoot;
    private final EditJournal journal;

    public FileTools(Path projectRoot) {
        this(projectRoot, new EditJournal());
    }

    public FileTools(Path projectRoot, EditJournal journal) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot")
                .toAbsolutePath()
                .normalize();
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    @Tool("Read the full contents of a text file inside the project. " +
          "The 'path' argument is relative to the project root. " +
          "Returns the file contents on success, or an error message starting with 'Error:' on failure.")
    public String readFile(
            @P("Path of the file to read, relative to the project root.") String path) {
        Path resolved = resolveOrError(path);
        if (resolved == null) return errorMessage;
        if (!Files.exists(resolved)) return "Error: file not found at '" + path + "'.";
        if (!Files.isRegularFile(resolved)) return "Error: '" + path + "' is not a regular file.";
        try {
            return Files.readString(resolved);
        } catch (IOException e) {
            return "Error: could not read '" + path + "': " + e.getMessage();
        }
    }

    @Tool("Overwrite a file inside the project with the given content. " +
          "The 'path' is relative to the project root. " +
          "Writing to test files (any path under 'src/test/') is forbidden — fix the production code, not the tests. " +
          "The parent directory must already exist. " +
          "Returns 'OK: <n> bytes written' on success, or an error message starting with 'Error:' on failure.")
    public String writeFile(
            @P("Path of the file to write, relative to the project root.") String path,
            @P("Full new content of the file. The file is overwritten, not appended.") String content) {
        Path resolved = resolveOrError(path);
        if (resolved == null) return errorMessage;
        if (content == null) return "Error: 'content' must not be null.";
        if (isTestPath(path, resolved)) {
            return "Error: writes to test paths are forbidden ('" + path + "'). Fix the production code instead.";
        }
        if (content.length() > MAX_WRITE_BYTES) {
            return "Error: content exceeds " + MAX_WRITE_BYTES + " byte cap.";
        }
        Path parent = resolved.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return "Error: parent directory of '" + path + "' does not exist.";
        }
        try {
            Files.writeString(resolved, content);
            journal.record(normalizeForward(path), content);
            return "OK: " + content.getBytes().length + " bytes written to '" + path + "'.";
        } catch (IOException e) {
            return "Error: could not write '" + path + "': " + e.getMessage();
        }
    }

    @Tool("List the immediate children of a directory inside the project. " +
          "The 'path' is relative to the project root; use an empty string or '.' for the root itself. " +
          "Returns one entry per line, directories first; each line starts with '[D]' or '[F]'. " +
          "Capped at " + LIST_LIMIT + " entries.")
    public String listFiles(
            @P("Directory path relative to the project root. Use '' or '.' for the root.") String path) {
        String effective = (path == null || path.isBlank() || path.equals(".")) ? "" : path;
        Path resolved = effective.isEmpty() ? projectRoot : resolveOrError(effective);
        if (resolved == null) return errorMessage;
        if (!Files.exists(resolved)) return "Error: directory not found at '" + path + "'.";
        if (!Files.isDirectory(resolved)) return "Error: '" + path + "' is not a directory.";

        try (Stream<Path> entries = Files.list(resolved)) {
            List<Path> sorted = entries
                    .sorted(Comparator
                            .comparing((Path p) -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))
                    .limit(LIST_LIMIT + 1L)
                    .toList();

            StringBuilder out = new StringBuilder();
            int shown = Math.min(sorted.size(), LIST_LIMIT);
            for (int i = 0; i < shown; i++) {
                Path p = sorted.get(i);
                boolean dir = Files.isDirectory(p);
                out.append(dir ? "[D] " : "[F] ").append(p.getFileName());
                if (!dir) {
                    try {
                        out.append("  ").append(Files.size(p)).append("B");
                    } catch (IOException ignored) {
                        // size unavailable; skip
                    }
                }
                out.append('\n');
            }
            if (sorted.size() > LIST_LIMIT) {
                out.append("… (truncated at ").append(LIST_LIMIT).append(" entries)\n");
            }
            return out.toString().stripTrailing();
        } catch (IOException e) {
            return "Error: could not list '" + path + "': " + e.getMessage();
        }
    }

    // --- helpers -----------------------------------------------------------

    // Set by resolveOrError when it returns null, so the caller can return it
    // to the LLM without losing the specific reason. Not thread-safe; tools
    // are invoked one at a time per agent run, which is fine.
    private String errorMessage;

    private Path resolveOrError(String path) {
        errorMessage = null;
        if (path == null || path.isBlank()) {
            errorMessage = "Error: 'path' must not be empty.";
            return null;
        }
        Path resolved;
        try {
            resolved = projectRoot.resolve(path).normalize();
        } catch (RuntimeException e) {
            errorMessage = "Error: invalid path '" + path + "': " + e.getMessage();
            return null;
        }
        if (!resolved.startsWith(projectRoot)) {
            errorMessage = "Error: path '" + path + "' escapes the project root.";
            return null;
        }
        return resolved;
    }

    static boolean isTestPath(String requestedPath, Path resolved) {
        String rel = normalizeForward(requestedPath);
        if (rel.startsWith("src/test/") || rel.contains("/src/test/")) return true;
        // Also block if any ancestor segment is literally "test" inside an "src" parent.
        String full = normalizeForward(resolved.toString());
        return full.contains("/src/test/");
    }

    private static String normalizeForward(String s) {
        if (s == null) return "";
        return s.replace('\\', '/');
    }

    Path projectRoot() {
        return projectRoot;
    }
}
