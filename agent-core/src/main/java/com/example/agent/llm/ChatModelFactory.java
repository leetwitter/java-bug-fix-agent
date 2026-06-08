package com.example.agent.llm;

import com.example.agent.core.AgentConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;

/**
 * Builds a LangChain4j {@link ChatModel} from an {@link AgentConfig}. The
 * provider is selected by config, never hard-coded at a call site.
 */
public final class ChatModelFactory {

    private ChatModelFactory() {}

    public static ChatModel create(AgentConfig config) {
        ChatModel model = switch (config.provider().toLowerCase()) {
            case "ollama" -> ollama(config);
            case "openai" -> openAi(config);
            default -> throw new IllegalArgumentException(
                    "Unknown provider '" + config.provider() + "'. Supported: ollama, openai");
        };
        // Optional client-side rate limiter for RPM-throttled endpoints (e.g. a free
        // OpenAI-compatible relay). Off by default; set AGENT_MIN_CALL_INTERVAL_MS to
        // the minimum gap between calls (60000 / requests-per-minute = ms per call).
        long minIntervalMillis = minCallIntervalMillis();
        return minIntervalMillis > 0 ? new ThrottledChatModel(model, minIntervalMillis) : model;
    }

    /** Reads AGENT_MIN_CALL_INTERVAL_MS; 0 (disabled) when unset, blank, or non-positive. */
    private static long minCallIntervalMillis() {
        String raw = System.getenv("AGENT_MIN_CALL_INTERVAL_MS");
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            System.err.println("[llm] ignoring non-numeric AGENT_MIN_CALL_INTERVAL_MS='" + raw + "'");
            return 0L;
        }
    }

    private static ChatModel ollama(AgentConfig config) {
        return OllamaChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .temperature(config.temperature())
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    private static ChatModel openAi(AgentConfig config) {
        String apiKey = config.openAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "AGENT_PROVIDER=openai requires OPENAI_API_KEY to be set.");
        }
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.modelName())
                .temperature(config.temperature())
                .timeout(Duration.ofMinutes(2));
        // Optional: point at any OpenAI-compatible endpoint (Azure OpenAI, DeepSeek,
        // Moonshot, a local proxy, …) without code changes. Left unset → api.openai.com.
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }
}
