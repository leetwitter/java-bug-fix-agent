package com.example.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A throwaway copy of a target project. Copying the template into a temp dir
 * and operating only on the copy means a benchmark run never has to "reset"
 * the original — the template stays pristine and the workspace is deleted on
 * {@link #close()}. Build artefact dirs are skipped so the copy is cheap and
 * free of stale caches.
 */
public final class BugWorkspace implements AutoCloseable {

    static final Set<String> SKIP_DIRS = Set.of("build", ".gradle", ".git");

    private final Path dir;

    private BugWorkspace(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    public static BugWorkspace copyOf(Path template) {
        if (!Files.isDirectory(template)) {
            throw new IllegalArgumentException("template is not a directory: " + template);
        }
        try {
            Path temp = Files.createTempDirectory("bugfix-" + safe(template.getFileName().toString()) + "-");
            copyTree(template, temp);
            return new BugWorkspace(temp);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create workspace for " + template, e);
        }
    }

    private static void copyTree(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path name = dir.getFileName();
                if (!dir.equals(src) && name != null && SKIP_DIRS.contains(name.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(dest.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, dest.resolve(src.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Override
    public void close() {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup; a leftover temp dir is not fatal
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
