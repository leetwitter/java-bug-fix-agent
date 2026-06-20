package com.example.agent.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTest {

    private static AiMessage toolCall(String id, String name) {
        return AiMessage.from(ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build());
    }

    private static ToolExecutionResultMessage toolResult(String id, String name, String text) {
        return ToolExecutionResultMessage.from(
                ToolExecutionRequest.builder().id(id).name(name).arguments("{}").build(), text);
    }

    @Test
    void fullMemoryReturnsEntireTranscriptUnchanged() {
        List<ChatMessage> transcript = List.of(
                SystemMessage.from("sys"),
                UserMessage.from("task"),
                toolCall("1", "runTests"),
                toolResult("1", "runTests", "FAILED"),
                toolCall("2", "readFile"),
                toolResult("2", "readFile", "code"));

        assertThat(ConversationMemory.full().view(transcript)).isEqualTo(transcript);
    }

    @Test
    void recentOnlyKeepsSystemTaskAndOnlyTheLastObservation() {
        ChatMessage sys = SystemMessage.from("sys");
        ChatMessage task = UserMessage.from("task");
        ChatMessage firstCall = toolCall("1", "runTests");
        ChatMessage firstResult = toolResult("1", "runTests", "FAILED");
        ChatMessage lastCall = toolCall("2", "readFile");
        ChatMessage lastResult = toolResult("2", "readFile", "code");
        List<ChatMessage> transcript = List.of(sys, task, firstCall, firstResult, lastCall, lastResult);

        List<ChatMessage> view = ConversationMemory.recentOnly().view(transcript);

        // system + task + the last tool-call/result pair; the earlier exchange is gone.
        assertThat(view).containsExactly(sys, task, lastCall, lastResult);
        assertThat(view).doesNotContain(firstCall, firstResult);
    }

    @Test
    void recentOnlyKeepsTheToolCallAttachedToItsResultForTranscriptValidity() {
        // A bare tool-result with no preceding assistant tool-call is rejected by
        // OpenAI-style providers; recentOnly must never produce that shape.
        List<ChatMessage> view = ConversationMemory.recentOnly().view(List.of(
                SystemMessage.from("sys"),
                UserMessage.from("task"),
                toolCall("9", "runTests"),
                toolResult("9", "runTests", "FAILED")));

        assertThat(view.get(view.size() - 2)).isInstanceOf(AiMessage.class);
        assertThat(view.get(view.size() - 1)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    @Test
    void recentOnlyOnFirstTurnIsJustSystemAndTask() {
        ChatMessage sys = SystemMessage.from("sys");
        ChatMessage task = UserMessage.from("task");

        List<ChatMessage> view = ConversationMemory.recentOnly().view(List.of(sys, task));

        assertThat(view).containsExactly(sys, task);
    }

    @Test
    void recentOnlyRetainsTrailingCriticFeedbackWithTheLastObservation() {
        ChatMessage sys = SystemMessage.from("sys");
        ChatMessage task = UserMessage.from("task");
        ChatMessage call = toolCall("1", "writeFile");
        ChatMessage result = toolResult("1", "writeFile", "OK");
        ChatMessage finalClaim = AiMessage.from("done");
        ChatMessage critic = UserMessage.from("reviewer rejected your change");
        List<ChatMessage> transcript = List.of(sys, task, call, result, finalClaim, critic);

        List<ChatMessage> view = ConversationMemory.recentOnly().view(transcript);

        assertThat(view).containsExactly(sys, task, call, result, finalClaim, critic);
    }
}
