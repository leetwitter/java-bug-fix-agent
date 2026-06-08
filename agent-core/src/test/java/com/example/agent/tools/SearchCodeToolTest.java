package com.example.agent.tools;

import com.example.agent.rag.Retriever;
import com.example.agent.rag.Symbol;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchCodeToolTest {

    @Test
    void rendersHitsWithFqnLinesAndSnippet() {
        Retriever retriever = mock(Retriever.class);
        when(retriever.search(eq("applyDiscount"), anyInt())).thenReturn(List.of(
                new Symbol(Symbol.Kind.METHOD, "applyDiscount", "com.example.Order.applyDiscount",
                        "applyDiscount(double) : double",
                        Path.of("src/main/java/com/example/Order.java"),
                        12, 18, "public double applyDiscount(double price) { return price * 0.9; }")));

        String out = new SearchCodeTool(retriever).searchCode("applyDiscount");

        assertThat(out)
                .contains("METHOD com.example.Order.applyDiscount")
                .contains("src/main/java/com/example/Order.java")
                .contains("lines 12-18")
                .contains("applyDiscount(double) : double")
                .contains("return price * 0.9;");
    }

    @Test
    void rendersWindowsPathsAsForwardSlash() {
        Retriever retriever = mock(Retriever.class);
        when(retriever.search(eq("q"), anyInt())).thenReturn(List.of(
                new Symbol(Symbol.Kind.CLASS, "C", "p.C", "class C",
                        Path.of("src", "main", "java", "p", "C.java"),
                        1, 1, "class C {}")));

        String out = new SearchCodeTool(retriever).searchCode("q");

        assertThat(out).contains("src/main/java/p/C.java");
        assertThat(out).doesNotContain("\\");
    }

    @Test
    void emptyResultProducesFriendlyMessage() {
        Retriever retriever = mock(Retriever.class);
        when(retriever.search(eq("nothing"), anyInt())).thenReturn(List.of());

        String out = new SearchCodeTool(retriever).searchCode("nothing");

        assertThat(out).contains("No matching symbols").contains("nothing");
    }
}
