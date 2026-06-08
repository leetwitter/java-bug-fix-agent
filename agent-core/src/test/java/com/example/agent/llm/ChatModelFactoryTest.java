package com.example.agent.llm;

import com.example.agent.core.AgentConfig;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Building a ChatModel is offline (no network call happens until {@code chat()}),
 * so provider wiring is unit-testable without a live model or an API key.
 */
class ChatModelFactoryTest {

    private static AgentConfig config(String provider, String apiKey) {
        return new AgentConfig(provider, "some-model", "http://localhost:11434", 0.2, 10, apiKey);
    }

    @Test
    void ollamaProviderBuilds() {
        ChatModel model = ChatModelFactory.create(config("ollama", null));
        assertThat(model).isNotNull();
    }

    @Test
    void openAiProviderBuildsWhenKeyPresent() {
        ChatModel model = ChatModelFactory.create(config("openai", "sk-test-key"));
        assertThat(model).isNotNull();
    }

    @Test
    void openAiProviderRequiresApiKey() {
        assertThatThrownBy(() -> ChatModelFactory.create(config("openai", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void providerSelectionIsCaseInsensitive() {
        assertThat(ChatModelFactory.create(config("OpenAI", "sk-test-key"))).isNotNull();
    }

    @Test
    void unknownProviderIsRejectedWithSupportedList() {
        assertThatThrownBy(() -> ChatModelFactory.create(config("anthropic", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ollama")
                .hasMessageContaining("openai");
    }
}
