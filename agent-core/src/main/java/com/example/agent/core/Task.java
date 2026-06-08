package com.example.agent.core;

import java.nio.file.Path;
import java.util.Objects;

public record Task(String prompt, Path projectRoot) {

    public Task {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(projectRoot, "projectRoot");
    }

    public static Task of(String prompt) {
        return new Task(prompt, Path.of("").toAbsolutePath());
    }
}
