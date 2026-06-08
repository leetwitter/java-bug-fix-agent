package com.example.agent.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory index of {@link Symbol}s, built once per agent run by
 * {@link SymbolIndexer}. Provides exact lookups by simple name and FQN;
 * fuzzy/ranked queries live in {@link SymbolRetriever}.
 */
public final class SymbolIndex {

    private final List<Symbol> all = new ArrayList<>();
    private final Map<String, List<Symbol>> bySimpleNameLower = new HashMap<>();
    private final Map<String, Symbol> byFqn = new HashMap<>();

    public void add(Symbol s) {
        all.add(s);
        bySimpleNameLower
                .computeIfAbsent(s.simpleName().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(s);
        byFqn.put(s.fqn(), s);
    }

    public List<Symbol> findBySimpleName(String name) {
        if (name == null) return List.of();
        return bySimpleNameLower.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    public Optional<Symbol> findByFqn(String fqn) {
        return Optional.ofNullable(byFqn.get(fqn));
    }

    public List<Symbol> all() {
        return List.copyOf(all);
    }

    public int size() {
        return all.size();
    }
}
