package com.example.agent.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThrottledChatModelTest {

    /** Records the wall-clock time of each call; returns a trivial response. */
    private static final class RecordingModel implements ChatModel {
        final List<Long> callTimesMillis = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            callTimesMillis.add(System.currentTimeMillis());
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    }

    private static ChatRequest anyRequest() {
        return ChatRequest.builder().messages(dev.langchain4j.data.message.UserMessage.from("hi")).build();
    }

    @Test
    void spacesSuccessiveCallsByAtLeastTheInterval() {
        RecordingModel delegate = new RecordingModel();
        long intervalMillis = 200;
        ThrottledChatModel throttled = new ThrottledChatModel(delegate, intervalMillis);

        for (int i = 0; i < 3; i++) {
            throttled.chat(anyRequest());
        }

        assertThat(delegate.callTimesMillis).hasSize(3);
        // Allow a little scheduler slack below the nominal interval.
        long slack = 30;
        for (int i = 1; i < delegate.callTimesMillis.size(); i++) {
            long gap = delegate.callTimesMillis.get(i) - delegate.callTimesMillis.get(i - 1);
            assertThat(gap)
                    .as("gap between call %d and %d", i - 1, i)
                    .isGreaterThanOrEqualTo(intervalMillis - slack);
        }
    }

    @Test
    void firstCallIsNotDelayed() {
        RecordingModel delegate = new RecordingModel();
        ThrottledChatModel throttled = new ThrottledChatModel(delegate, 5_000);

        long before = System.currentTimeMillis();
        throttled.chat(anyRequest());
        long elapsed = System.currentTimeMillis() - before;

        assertThat(elapsed).isLessThan(1_000);
        assertThat(delegate.callTimesMillis).hasSize(1);
    }

    @Test
    void rejectsNonPositiveInterval() {
        RecordingModel delegate = new RecordingModel();
        assertThatThrownBy(() -> new ThrottledChatModel(delegate, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
