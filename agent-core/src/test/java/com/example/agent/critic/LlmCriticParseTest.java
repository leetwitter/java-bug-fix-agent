package com.example.agent.critic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCriticParseTest {

    @Test
    void approvesOnApproveVerdict() {
        Critique c = LlmCritic.parse("VERDICT: APPROVE");
        assertThat(c.approved()).isTrue();
    }

    @Test
    void revisesWithMultilineFeedback() {
        Critique c = LlmCritic.parse("VERDICT: REVISE\nThe package declaration is missing.");
        assertThat(c.approved()).isFalse();
        assertThat(c.feedback()).contains("package declaration is missing");
    }

    @Test
    void revisesWithInlineFeedbackOnVerdictLine() {
        Critique c = LlmCritic.parse("VERDICT: REVISE you dropped the package line");
        assertThat(c.approved()).isFalse();
        assertThat(c.feedback()).contains("dropped the package");
    }

    @Test
    void isCaseInsensitive() {
        assertThat(LlmCritic.parse("verdict: revise nope").approved()).isFalse();
        assertThat(LlmCritic.parse("verdict: approve").approved()).isTrue();
    }

    @Test
    void skipsLeadingBlankLinesBeforeVerdict() {
        Critique c = LlmCritic.parse("\n\n   \nVERDICT: REVISE fix it");
        assertThat(c.approved()).isFalse();
    }

    @Test
    void failsOpenOnNullOrBlank() {
        assertThat(LlmCritic.parse(null).approved()).isTrue();
        assertThat(LlmCritic.parse("   ").approved()).isTrue();
    }

    @Test
    void failsOpenWhenFirstLineIsNotAVerdict() {
        // A confused critic that ignored the contract must never deadlock the loop.
        assertThat(LlmCritic.parse("Sure, here are my thoughts...\nVERDICT: REVISE").approved())
                .isTrue();
    }

    @Test
    void reviseWithNoFeedbackStillCarriesAMessage() {
        Critique c = LlmCritic.parse("VERDICT: REVISE");
        assertThat(c.approved()).isFalse();
        assertThat(c.feedback()).isNotBlank();
    }
}
