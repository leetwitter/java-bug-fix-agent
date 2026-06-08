package com.example.agent.core;

import com.example.agent.critic.CritiqueMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AblationConfigTest {

    @Test
    void matrixCoversAllFourCornersExactlyOnce() {
        List<AblationConfig> matrix = AblationConfig.matrix();

        assertThat(matrix).hasSize(4);
        assertThat(matrix).doesNotHaveDuplicates();
        assertThat(matrix.stream().map(AblationConfig::label).collect(Collectors.toList()))
                .containsExactly("BASE", "RAG", "CRITIC", "RAG+CRITIC");
    }

    @Test
    void labelEncodesBothAxes() {
        assertThat(new AblationConfig(false, CritiqueMode.NONE).label()).isEqualTo("BASE");
        assertThat(new AblationConfig(true, CritiqueMode.NONE).label()).isEqualTo("RAG");
        assertThat(new AblationConfig(false, CritiqueMode.CRITIC).label()).isEqualTo("CRITIC");
        assertThat(new AblationConfig(true, CritiqueMode.CRITIC).label()).isEqualTo("RAG+CRITIC");
    }

    @Test
    void nullCritiqueIsRejected() {
        assertThatThrownBy(() -> new AblationConfig(true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
