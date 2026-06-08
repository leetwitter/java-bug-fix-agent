package com.example.benchmark;

import java.nio.file.Path;

/**
 * One seeded bug in the benchmark. The {@code templateDir} is a pristine,
 * already-buggy target project; the harness copies it to a scratch workspace
 * before each run so the template is never mutated.
 */
public record BenchmarkCase(String id, Path templateDir, String prompt) {
}
