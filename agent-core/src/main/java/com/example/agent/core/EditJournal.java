package com.example.agent.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records the files a repair run wrote, so a {@code Critic} can review the
 * actual result of the agent's edits. Insertion-ordered; a later write to the
 * same path replaces the earlier content. This is the only channel through
 * which the critic learns what changed — it never sees a reference solution.
 */
public final class EditJournal {

    private final Map<String, String> filesByPath = new LinkedHashMap<>();

    public void record(String relativePath, String content) {
        if (relativePath == null) {
            return;
        }
        filesByPath.put(relativePath, content == null ? "" : content);
    }

    public boolean isEmpty() {
        return filesByPath.isEmpty();
    }

    public int size() {
        return filesByPath.size();
    }

    /** A human-readable snapshot of every changed file, for review. */
    public String snapshot() {
        if (filesByPath.isEmpty()) {
            return "(no files were changed)";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : filesByPath.entrySet()) {
            sb.append("=== ").append(e.getKey()).append(" ===\n")
              .append(e.getValue()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }
}
