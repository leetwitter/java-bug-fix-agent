package com.example.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.mcp.protocol.McpImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end at the protocol seam: drive {@link McpServer#handle(JsonNode)} with
 * real JSON-RPC messages (no stdio needed) and assert the server lists and
 * executes the agent's tools. Satisfies the Phase 5 "call readFile over the
 * protocol" smoke test.
 */
class McpServerSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static McpServer serverFor(Path projectRoot) {
        List<Object> tools = McpServerTools.forProject(projectRoot);
        return new McpServer(tools, new McpImplementation("test-server", "0.0.1"));
    }

    private static String send(McpServer server, String jsonRpc) throws Exception {
        JsonNode request = MAPPER.readTree(jsonRpc);
        Object response = server.handle(request);
        return MAPPER.writeValueAsString(response);
    }

    private static void initialize(McpServer server) throws Exception {
        send(server, """
                {"jsonrpc":"2.0","id":0,"method":"initialize",
                 "params":{"protocolVersion":"2024-11-05","capabilities":{},
                           "clientInfo":{"name":"test","version":"1.0"}}}""");
    }

    @Test
    void listsTheAgentTools(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Hello.java"), "class Hello {}");
        McpServer server = serverFor(root);
        initialize(server);

        String listed = send(server, """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""");

        assertThat(listed).contains("readFile");
        assertThat(listed).contains("writeFile");
        assertThat(listed).contains("listFiles");
        assertThat(listed).contains("searchCode");
    }

    @Test
    void readFileToolReturnsFileContentsOverTheProtocol(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("greeting.txt"), "hello from mcp");
        McpServer server = serverFor(root);
        initialize(server);

        String result = send(server, """
                {"jsonrpc":"2.0","id":2,"method":"tools/call",
                 "params":{"name":"readFile","arguments":{"path":"greeting.txt"}}}""");

        assertThat(result).contains("hello from mcp");
    }
}
