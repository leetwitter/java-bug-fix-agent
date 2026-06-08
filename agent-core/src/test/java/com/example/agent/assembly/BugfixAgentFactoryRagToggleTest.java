package com.example.agent.assembly;

import com.example.agent.assembly.BugfixAgentFactory.Assembled;
import com.example.agent.core.AgentConfig;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.observability.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The RAG axis of the ablation must actually change what the agent is given.
 * Assembly is offline (the Ollama client is built but never called here).
 */
class BugfixAgentFactoryRagToggleTest {

    private static AgentConfig ollamaConfig() {
        return new AgentConfig("ollama", "test-model", "http://localhost:11434", 0.0, 5, null);
    }

    private static Path seedProject(Path root) throws IOException {
        Path src = root.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Foo.java"),
                "package com.example; public class Foo { int bar() { return 1; } }");
        return root;
    }

    @Test
    void retrievalOnBuildsTheSymbolIndex(@TempDir Path root) throws IOException {
        seedProject(root);
        Assembled a = BugfixAgentFactory.assemble(
                root, ollamaConfig(), CritiqueMode.NONE, null, Tracer.noop(), true);
        assertThat(a.symbolCount()).isGreaterThan(0);
    }

    @Test
    void retrievalOffSkipsTheSymbolIndexEntirely(@TempDir Path root) throws IOException {
        seedProject(root);
        Assembled a = BugfixAgentFactory.assemble(
                root, ollamaConfig(), CritiqueMode.NONE, null, Tracer.noop(), false);
        assertThat(a.symbolCount()).isZero();
    }

    @Test
    void defaultAssembleKeepsRetrievalOn(@TempDir Path root) throws IOException {
        seedProject(root);
        Assembled a = BugfixAgentFactory.assemble(
                root, ollamaConfig(), CritiqueMode.NONE, null, Tracer.noop());
        assertThat(a.symbolCount()).isGreaterThan(0);
    }
}
