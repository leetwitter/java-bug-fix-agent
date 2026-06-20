package com.example.agent.core;

import com.example.agent.critic.CritiqueMode;

import java.util.ArrayList;
import java.util.List;

/**
 * One cell of the ablation matrix: a combination of agent feature toggles whose
 * marginal contribution we want to measure.
 *
 * <p>Phase 4 ships all three axes from PLAN.md §3 — AST retrieval (RAG),
 * conversation memory, and the self-critique completion gate — giving the full
 * {@code 2x2x2} matrix ({@link #matrix()} returns 8 cells). Isolating each axis
 * lets us read off both marginal contributions and interaction effects (e.g.
 * does the critic only help once RAG is locating the right code?).
 *
 * @param retrieval whether the {@code searchCode} AST retrieval tool is offered
 * @param memory    whether the agent keeps full reason→act→observe history
 *                  ({@code true}) or is amnesiac, seeing only the system prompt,
 *                  the task, and the most recent observation ({@code false})
 * @param critique  the completion-gate mode (here: {@code NONE} or {@code CRITIC})
 */
public record AblationConfig(boolean retrieval, boolean memory, CritiqueMode critique) {

    public AblationConfig {
        if (critique == null) {
            throw new IllegalArgumentException("critique mode must not be null");
        }
    }

    /**
     * A short, stable label used as the CSV {@code config} column and the matrix
     * row header. Built by joining the active axes in a fixed order
     * (RAG, MEM, CRITIC), e.g. {@code "RAG+MEM+CRITIC"}, {@code "RAG+CRITIC"},
     * {@code "MEM"}, or {@code "BASE"} when every axis is off.
     */
    public String label() {
        List<String> parts = new ArrayList<>();
        if (retrieval) parts.add("RAG");
        if (memory) parts.add("MEM");
        if (critique == CritiqueMode.CRITIC) parts.add("CRITIC");
        return parts.isEmpty() ? "BASE" : String.join("+", parts);
    }

    /**
     * The full 2x2x2 matrix (RAG x memory x self-critique), ordered baseline-first
     * (no features) up to all features on, mirroring the PLAN.md §3 table so the
     * report reads bottom-up.
     */
    public static List<AblationConfig> matrix() {
        CritiqueMode none = CritiqueMode.NONE;
        CritiqueMode crit = CritiqueMode.CRITIC;
        return List.of(
                new AblationConfig(false, false, none),  // BASE
                new AblationConfig(true,  false, none),  // RAG
                new AblationConfig(false, true,  none),  // MEM
                new AblationConfig(false, false, crit),  // CRITIC
                new AblationConfig(true,  true,  none),  // RAG+MEM
                new AblationConfig(true,  false, crit),  // RAG+CRITIC
                new AblationConfig(false, true,  crit),  // MEM+CRITIC
                new AblationConfig(true,  true,  crit)); // RAG+MEM+CRITIC
    }
}
