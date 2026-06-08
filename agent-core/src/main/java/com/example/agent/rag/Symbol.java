package com.example.agent.rag;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One indexed program symbol from a target project's Java sources.
 *
 * @param kind          class / interface, method, or field
 * @param simpleName    e.g. {@code applyDiscount}
 * @param fqn           e.g. {@code com.example.OrderCalculator.applyDiscount}
 * @param signature     compact, human-readable; used in retriever output
 * @param relativePath  path of the source file, relative to the project root
 * @param startLine     1-based, inclusive
 * @param endLine       1-based, inclusive
 * @param snippet       source code of this symbol, possibly truncated
 */
public record Symbol(
        Kind kind,
        String simpleName,
        String fqn,
        String signature,
        Path relativePath,
        int startLine,
        int endLine,
        String snippet) {

    public enum Kind { CLASS, METHOD, FIELD }

    public Symbol {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(simpleName);
        Objects.requireNonNull(fqn);
        Objects.requireNonNull(signature);
        Objects.requireNonNull(relativePath);
        Objects.requireNonNull(snippet);
    }
}
