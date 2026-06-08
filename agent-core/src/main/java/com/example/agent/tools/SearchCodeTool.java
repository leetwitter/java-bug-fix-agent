package com.example.agent.tools;

import com.example.agent.rag.Retriever;
import com.example.agent.rag.Symbol;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.Objects;

/**
 * Lets the agent locate code by identifier or keyword without dumping the
 * whole project into context. Wraps a {@link Retriever}; current
 * implementation is symbol/AST-aware via JavaParser.
 */
public class SearchCodeTool {

    private static final int DEFAULT_TOP_K = 5;

    private final Retriever retriever;

    public SearchCodeTool(Retriever retriever) {
        this.retriever = Objects.requireNonNull(retriever, "retriever");
    }

    @Tool("Search the project's Java code for relevant classes, methods, and fields. " +
          "The query is treated as a set of identifier tokens; matching is by name " +
          "(exact > FQN ancestor > substring). " +
          "Use this BEFORE readFile when you don't yet know which file contains " +
          "the code you need. Returns up to 5 ranked symbols with file path, " +
          "line range, signature, and source snippet.")
    public String searchCode(
            @P("Identifier names or keywords. Multi-token queries score higher when " +
               "more tokens match. Example: 'OrderCalculator applyDiscount'.") String query) {
        List<Symbol> hits = retriever.search(query, DEFAULT_TOP_K);
        if (hits.isEmpty()) {
            return "No matching symbols found for query: " + query;
        }
        StringBuilder out = new StringBuilder();
        out.append("Found ").append(hits.size()).append(" matching symbol(s):\n");
        for (Symbol s : hits) {
            out.append("\n--- ").append(s.kind()).append(' ').append(s.fqn()).append('\n');
            out.append("file: ").append(s.relativePath().toString().replace('\\', '/'))
                    .append("  lines ").append(s.startLine()).append('-').append(s.endLine()).append('\n');
            out.append("signature: ").append(s.signature()).append('\n');
            out.append("snippet:\n").append(s.snippet()).append('\n');
        }
        return out.toString();
    }
}
