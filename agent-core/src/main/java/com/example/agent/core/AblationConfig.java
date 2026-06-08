package com.example.agent.core;

import com.example.agent.critic.CritiqueMode;

import java.util.List;

/**
 * One cell of the ablation matrix: a combination of agent feature toggles whose
 * marginal contribution we want to measure.
 *
 * <p>Phase 4 ships the two implemented axes — AST retrieval (RAG) and the
 * self-critique completion gate. The {@code memory} axis from PLAN.md is left
 * out until a cross-turn memory component exists; when it lands, add a field
 * here and extend {@link #matrix()} to a 2x2x2 grid without touching callers.
 *
 * @param retrieval whether the {@code searchCode} AST retrieval tool is offered
 * @param critique  the completion-gate mode (here: {@code NONE} or {@code CRITIC})
 */
public record AblationConfig(boolean retrieval, CritiqueMode critique) {

    public AblationConfig {
        if (critique == null) {
            throw new IllegalArgumentException("critique mode must not be null");
        }
    }

    /**
     * A short, stable label used as the CSV {@code config} column and the matrix
     * row header, e.g. {@code "RAG+CRITIC"}, {@code "RAG"}, {@code "CRITIC"},
     * {@code "BASE"}.
     */
    public String label() {
        boolean critic = critique == CritiqueMode.CRITIC;
        if (retrieval && critic) return "RAG+CRITIC";
        if (retrieval)           return "RAG";
        if (critic)              return "CRITIC";
        return "BASE";
    }

    /**
     * The full 2-axis matrix (RAG on/off x critique on/off), ordered from the
     * bare baseline up to all features on so the report reads bottom-up.
     */
    public static List<AblationConfig> matrix() {
        return List.of(
                new AblationConfig(false, CritiqueMode.NONE),    // BASE
                new AblationConfig(true, CritiqueMode.NONE),     // RAG
                new AblationConfig(false, CritiqueMode.CRITIC),  // CRITIC
                new AblationConfig(true, CritiqueMode.CRITIC));  // RAG+CRITIC
    }
}
