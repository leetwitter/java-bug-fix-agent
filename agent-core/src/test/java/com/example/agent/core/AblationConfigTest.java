package com.example.agent.core;

import com.example.agent.critic.CritiqueMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AblationConfigTest {

    @Test
    void matrixCoversAllEightCornersExactlyOnce() {
        List<AblationConfig> matrix = AblationConfig.matrix();

        assertThat(matrix).hasSize(8);
        assertThat(matrix).doesNotHaveDuplicates();
        assertThat(matrix.stream().map(AblationConfig::label).collect(Collectors.toList()))
                .containsExactly(
                        "BASE", "RAG", "MEM", "CRITIC",
                        "RAG+MEM", "RAG+CRITIC", "MEM+CRITIC", "RAG+MEM+CRITIC");
    }

    @Test
    void labelEncodesAllThreeAxesInFixedOrder() {
        assertThat(new AblationConfig(false, false, CritiqueMode.NONE).label()).isEqualTo("BASE");
        assertThat(new AblationConfig(true, false, CritiqueMode.NONE).label()).isEqualTo("RAG");
        assertThat(new AblationConfig(false, true, CritiqueMode.NONE).label()).isEqualTo("MEM");
        assertThat(new AblationConfig(false, false, CritiqueMode.CRITIC).label()).isEqualTo("CRITIC");
        assertThat(new AblationConfig(true, true, CritiqueMode.NONE).label()).isEqualTo("RAG+MEM");
        assertThat(new AblationConfig(true, false, CritiqueMode.CRITIC).label()).isEqualTo("RAG+CRITIC");
        assertThat(new AblationConfig(false, true, CritiqueMode.CRITIC).label()).isEqualTo("MEM+CRITIC");
        assertThat(new AblationConfig(true, true, CritiqueMode.CRITIC).label()).isEqualTo("RAG+MEM+CRITIC");
    }

    @Test
    void nullCritiqueIsRejected() {
        assertThatThrownBy(() -> new AblationConfig(true, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
