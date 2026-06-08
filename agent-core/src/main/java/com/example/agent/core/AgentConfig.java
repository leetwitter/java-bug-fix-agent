package com.example.agent.core;

public record AgentConfig(
        String provider,
        String modelName,
        String baseUrl,
        double temperature,
        int maxIterations,
        String openAiApiKey) {

    public static AgentConfig defaults() {
        return new AgentConfig(
                "ollama",
                // Instruct model: emits structured Ollama tool_calls. The
                // coder variant returns tool calls as plain text, which breaks
                // the agent loop (hasToolExecutionRequests() stays false).
                "qwen2.5:7b",
                "http://localhost:11434",
                0.2,
                10,
                null);
    }

    public static AgentConfig fromEnv() {
        AgentConfig d = defaults();
        return new AgentConfig(
                env("AGENT_PROVIDER", d.provider),
                env("AGENT_MODEL", d.modelName),
                env("OLLAMA_BASE_URL", d.baseUrl),
                envDouble("AGENT_TEMPERATURE", d.temperature),
                envInt("AGENT_MAX_ITERATIONS", d.maxIterations),
                env("OPENAI_API_KEY", null));
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static double envDouble(String name, double fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return fallback;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return fallback; }
    }

    private static int envInt(String name, int fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }
}
