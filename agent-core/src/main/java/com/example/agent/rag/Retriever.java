package com.example.agent.rag;

import java.util.List;

/**
 * Retrieves project symbols relevant to a free-text query. Phase 2 ships a
 * symbol/AST-aware implementation ({@link SymbolRetriever}); a dense-vector
 * implementation can be slotted in behind this interface in a later phase
 * for ablation comparisons.
 */
public interface Retriever {

    List<Symbol> search(String query, int topK);
}
