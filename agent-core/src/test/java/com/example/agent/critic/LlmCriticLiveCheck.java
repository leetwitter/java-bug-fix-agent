package com.example.agent.critic;

import com.example.agent.core.AgentConfig;
import com.example.agent.llm.ChatModelFactory;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live verification against a real Ollama model — NOT a unit test. Disabled by
 * default (requires Ollama running with the instruct model). Run explicitly:
 *
 * <pre>./gradlew :agent-core:test --tests "*LlmCriticLiveCheck" -PrunLive</pre>
 *
 * Proves the critic discriminates: it must REVISE a fix that dropped the
 * {@code package} declaration while the tests are red, and APPROVE a correct,
 * compilable fix whose tests are green.
 */
@Disabled("live model check; enable manually")
class LlmCriticLiveCheck {

    // Same body, but the BAD variant is missing `package com.example.calc;`
    private static final String BODY = """
            public final class Average {
                private Average() {}
                public static double compute(int[] xs) {
                    if (xs == null || xs.length == 0) {
                        throw new IllegalArgumentException("empty input");
                    }
                    long sum = 0;
                    for (int x : xs) sum += x;
                    return (double) sum / xs.length;
                }
            }
            """;
    private static final String BAD = BODY; // dropped package -> won't compile
    private static final String GOOD = "package com.example.calc;\n\n" + BODY;

    private static final String TASK = "Fix the failing tests in Average.compute";
    private static final String PATH = "src/main/java/com/example/calc/Average.java";

    @Test
    void rejectsDroppedPackageApprovesCorrectFix() {
        ChatModel model = ChatModelFactory.create(AgentConfig.fromEnv());
        Critic critic = new LlmCritic(model);

        Critique bad = critic.review(TASK, PATH + ":\n" + BAD,
                "FAILED (exit=1): compilation failed; AverageTest still red");
        System.out.println("[BAD ] approved=" + bad.approved() + " :: " + bad.feedback());

        Critique good = critic.review(TASK, PATH + ":\n" + GOOD,
                "PASSED (exit=0): all 5 AverageTest cases green");
        System.out.println("[GOOD] approved=" + good.approved() + " :: " + good.feedback());

        assertThat(bad.approved()).as("must reject dropped-package fix").isFalse();
        assertThat(good.approved()).as("must approve correct fix").isTrue();
    }
}
