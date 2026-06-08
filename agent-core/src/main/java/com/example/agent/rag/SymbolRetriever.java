package com.example.agent.rag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ranks {@link Symbol}s against an identifier-style query.
 *
 * <p>Scoring (per query token):
 * <ul>
 *   <li>+10 if a symbol's simple name equals the token</li>
 *   <li>+3  if a symbol's simple name contains the token</li>
 *   <li>+5 / +1 same as above but applied to the FQN ancestry (catches
 *       {@code com.example.OrderCalculator} when the token is {@code Order})</li>
 *   <li>kind boost added once per matched symbol: method 2, class 1, field 0.5</li>
 * </ul>
 * Tokens shorter than 2 characters are ignored.
 */
public final class SymbolRetriever implements Retriever {

    private final SymbolIndex index;

    public SymbolRetriever(SymbolIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public List<Symbol> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) return List.of();

        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : query.split("[^A-Za-z0-9_]+")) {
            if (raw.length() >= 2) tokens.add(raw.toLowerCase(Locale.ROOT));
        }
        if (tokens.isEmpty()) return List.of();

        Map<Symbol, Double> scores = new HashMap<>();

        for (String tok : tokens) {
            // 1) exact simple-name hits
            for (Symbol s : index.findBySimpleName(tok)) {
                scores.merge(s, 10.0 + kindBoost(s), Double::sum);
            }
            // 2) substring scans (one pass over all symbols for this token)
            for (Symbol s : index.all()) {
                String simple = s.simpleName().toLowerCase(Locale.ROOT);
                String fqn = s.fqn().toLowerCase(Locale.ROOT);
                if (simple.equals(tok)) {
                    // already counted via findBySimpleName above
                    continue;
                }
                if (simple.contains(tok)) {
                    scores.merge(s, 3.0 + kindBoost(s), Double::sum);
                } else if (fqn.contains(tok) && Arrays.asList(fqn.split("\\.")).contains(tok)) {
                    scores.merge(s, 5.0 + kindBoost(s), Double::sum);
                } else if (fqn.contains(tok)) {
                    scores.merge(s, 1.0 + kindBoost(s), Double::sum);
                }
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<Symbol, Double>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().relativePath().toString())
                        .thenComparing(e -> e.getKey().startLine()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static double kindBoost(Symbol s) {
        return switch (s.kind()) {
            case METHOD -> 2.0;
            case CLASS  -> 1.0;
            case FIELD  -> 0.5;
        };
    }
}
