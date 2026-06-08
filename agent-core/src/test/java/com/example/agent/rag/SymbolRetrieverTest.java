package com.example.agent.rag;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolRetrieverTest {

    private static Symbol symbol(Symbol.Kind kind, String simple, String fqn) {
        return new Symbol(kind, simple, fqn, simple, Path.of("X.java"), 1, 2, "// ...");
    }

    @Test
    void rankExactNameMatchHighestForQueryToken() {
        SymbolIndex idx = new SymbolIndex();
        Symbol exact = symbol(Symbol.Kind.METHOD, "applyDiscount", "com.example.Order.applyDiscount");
        Symbol substring = symbol(Symbol.Kind.METHOD, "applyDiscountedPrice", "com.example.Order.applyDiscountedPrice");
        Symbol unrelated = symbol(Symbol.Kind.METHOD, "irrelevant", "com.example.Other.irrelevant");
        idx.add(exact);
        idx.add(substring);
        idx.add(unrelated);

        List<Symbol> hits = new SymbolRetriever(idx).search("applyDiscount", 5);

        assertThat(hits).first().isEqualTo(exact);
        assertThat(hits).contains(substring);
        assertThat(hits).doesNotContain(unrelated);
    }

    @Test
    void multiTokenQueriesScoreHigher() {
        SymbolIndex idx = new SymbolIndex();
        Symbol both = symbol(Symbol.Kind.METHOD, "applyDiscount", "com.example.OrderCalculator.applyDiscount");
        Symbol oneToken = symbol(Symbol.Kind.METHOD, "applyDiscount", "com.example.Foo.applyDiscount");
        idx.add(both);
        idx.add(oneToken);

        List<Symbol> hits = new SymbolRetriever(idx).search("OrderCalculator applyDiscount", 5);

        // 'both' matches both tokens (one via FQN ancestry, one via simple name); should rank first.
        assertThat(hits).first().isEqualTo(both);
    }

    @Test
    void prefersMethodsOverFieldsAtSameMatchLevel() {
        SymbolIndex idx = new SymbolIndex();
        Symbol method = symbol(Symbol.Kind.METHOD, "rate", "com.example.X.rate");
        Symbol field  = symbol(Symbol.Kind.FIELD,  "rate", "com.example.X.rate");
        idx.add(field);
        idx.add(method);

        List<Symbol> hits = new SymbolRetriever(idx).search("rate", 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0)).isEqualTo(method);
        assertThat(hits.get(1)).isEqualTo(field);
    }

    @Test
    void ignoresTokensShorterThanTwoChars() {
        SymbolIndex idx = new SymbolIndex();
        idx.add(symbol(Symbol.Kind.METHOD, "a", "com.example.X.a"));

        List<Symbol> hits = new SymbolRetriever(idx).search("a", 5);

        assertThat(hits).isEmpty();
    }

    @Test
    void returnsEmptyForBlankQuery() {
        SymbolIndex idx = new SymbolIndex();
        idx.add(symbol(Symbol.Kind.METHOD, "anything", "com.example.X.anything"));

        assertThat(new SymbolRetriever(idx).search("", 5)).isEmpty();
        assertThat(new SymbolRetriever(idx).search("   ", 5)).isEmpty();
        assertThat(new SymbolRetriever(idx).search(null, 5)).isEmpty();
    }

    @Test
    void respectsTopKLimit() {
        SymbolIndex idx = new SymbolIndex();
        for (int i = 0; i < 10; i++) {
            idx.add(symbol(Symbol.Kind.METHOD, "compute", "p.C" + i + ".compute"));
        }

        List<Symbol> hits = new SymbolRetriever(idx).search("compute", 3);

        assertThat(hits).hasSize(3);
    }
}
