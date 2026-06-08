package com.example.agent.mcp;

import com.example.agent.core.EditJournal;
import com.example.agent.exec.TestRunner;
import com.example.agent.rag.SymbolIndex;
import com.example.agent.rag.SymbolIndexer;
import com.example.agent.rag.SymbolRetriever;
import com.example.agent.tools.FileTools;
import com.example.agent.tools.SearchCodeTool;
import com.example.agent.tools.TestRunnerTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the same {@code @Tool}-annotated tool objects the in-process agent uses
 * ({@link FileTools}, {@link SearchCodeTool}, {@link TestRunnerTool}), so the MCP
 * server exposes exactly the tools the agent has. Transport (stdio) is wired
 * separately in {@link McpServerMain}; this class is pure assembly and is unit
 * tested without touching stdin/stdout.
 */
final class McpServerTools {

    private McpServerTools() {}

    /**
     * @param projectRoot the project the tools operate on
     * @return tool holder objects; {@code runTests} is omitted when the project
     *         has no recognizable build tool
     */
    static List<Object> forProject(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");

        List<Object> tools = new ArrayList<>();
        tools.add(new FileTools(projectRoot, new EditJournal()));

        SymbolIndex symbolIndex = new SymbolIndexer().index(projectRoot);
        tools.add(new SearchCodeTool(new SymbolRetriever(symbolIndex)));

        try {
            tools.add(new TestRunnerTool(TestRunner.forProject(projectRoot)));
        } catch (IllegalArgumentException e) {
            System.err.println("[mcp] no recognizable build tool; runTests is disabled");
        }
        return tools;
    }
}
