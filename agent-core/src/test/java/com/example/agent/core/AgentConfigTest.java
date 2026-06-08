package com.example.agent.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConfigTest {

    @Test
    void defaultsAreOllamaWithSensibleSettings() {
        AgentConfig c = AgentConfig.defaults();

        assertThat(c.provider()).isEqualTo("ollama");
        assertThat(c.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(c.maxIterations()).isPositive();
        assertThat(c.temperature()).isBetween(0.0, 2.0);
        assertThat(c.modelName()).isNotBlank();
    }
}
