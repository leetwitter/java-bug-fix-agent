package com.example.agent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code memory} axis of the ablation matrix (PLAN.md §3): decides which
 * slice of the running reason→act→observe transcript the model actually sees on
 * each turn.
 *
 * <ul>
 *   <li>{@link #full()} — the agent keeps the entire history. This is the
 *       production default: every prior reasoning step, tool call, and
 *       observation stays in context.</li>
 *   <li>{@link #recentOnly()} — the agent is amnesiac. Every turn it sees only
 *       the system prompt, the original task, and the <em>most recent
 *       observation</em> (the last tool-call/result exchange). Earlier history is
 *       dropped, exactly as PLAN.md §3 specifies for {@code memory=off}.</li>
 * </ul>
 *
 * <p>{@link #recentOnly()} keeps the most-recent assistant tool-call message
 * attached to its tool results on purpose: an OpenAI-style provider rejects a
 * tool-result message that is not immediately preceded by the assistant message
 * that requested it, so the "most recent observation" must carry its triggering
 * tool call to remain a valid transcript.
 */
@FunctionalInterface
public interface ConversationMemory {

    /** The messages to send to the model this turn, given the full transcript. */
    List<ChatMessage> view(List<ChatMessage> transcript);

    /** Full history — the production default. */
    static ConversationMemory full() {
        return transcript -> transcript;
    }

    /** Amnesiac: system prompt + original task + most recent observation only. */
    static ConversationMemory recentOnly() {
        return transcript -> {
            List<ChatMessage> view = new ArrayList<>();

            // The system prompt and the original task are always retained — they
            // are the agent's standing instructions and goal, not "history".
            firstOfType(transcript, SystemMessage.class).ifPresent(view::add);
            firstOfType(transcript, UserMessage.class).ifPresent(view::add);

            // The most recent observation = everything from the last assistant
            // message that requested tools through the end of the transcript
            // (its tool results, plus any trailing critic feedback).
            int lastToolTurn = -1;
            for (int i = 0; i < transcript.size(); i++) {
                if (transcript.get(i) instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                    lastToolTurn = i;
                }
            }
            if (lastToolTurn >= 0) {
                view.addAll(transcript.subList(lastToolTurn, transcript.size()));
            }
            return view;
        };
    }

    private static <T extends ChatMessage> java.util.Optional<ChatMessage> firstOfType(
            List<ChatMessage> messages, Class<T> type) {
        for (ChatMessage m : messages) {
            if (type.isInstance(m)) {
                return java.util.Optional.of(m);
            }
        }
        return java.util.Optional.empty();
    }
}
