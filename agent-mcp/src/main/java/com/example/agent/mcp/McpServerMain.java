package com.example.agent.mcp;

import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;

import java.nio.file.Path;
import java.util.List;

/**
 * Entry point for {@code ./gradlew :agent-mcp:run --args="<projectPath>"}: a stdio
 * MCP server that exposes this project's file/search/test tools to any MCP client
 * (an IDE, Claude Desktop, another agent) over JSON-RPC on stdin/stdout.
 *
 * <p>The tools are the very same {@code @Tool} objects the in-process agent uses
 * (assembled by {@link McpServerTools}); only the transport differs. All logging
 * goes to {@code System.err} — {@code System.out} is the protocol channel and must
 * not be polluted.
 */
public final class McpServerMain {

    private static final String SERVER_NAME = "java-bugfix-agent";
    private static final String SERVER_VERSION = "1.0.0";

    private McpServerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: agent-mcp <projectPath>");
            System.exit(2);
            return;
        }
        Path projectRoot = Path.of(args[0]).toAbsolutePath().normalize();
        List<Object> tools = McpServerTools.forProject(projectRoot);

        McpServer server = new McpServer(tools, new McpImplementation(SERVER_NAME, SERVER_VERSION));
        System.err.println("[mcp] serving " + tools.size() + " tool group(s) for " + projectRoot
                + " over stdio");

        try (StdioMcpServerTransport transport =
                     new StdioMcpServerTransport(System.in, System.out, server)) {
            transport.awaitClose();
        }
    }
}
