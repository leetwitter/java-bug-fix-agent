package com.example.agent.core;

import com.example.agent.critic.Critic;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.memory.ConversationMemory;
import com.example.agent.observability.Tracer;
import com.example.agent.tools.FileTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTest {

    private static AgentConfig configWithMaxIterations(int max) {
        return new AgentConfig("ollama", "test-model", "http://localhost", 0.0, max, null);
    }

    private static ChatResponse responseOf(AiMessage aiMessage) {
        return ChatResponse.builder().aiMessage(aiMessage).build();
    }

    private static ChatModel modelReturning(Deque<AiMessage> scripted) {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class)))
                .thenAnswer(inv -> responseOf(scripted.pollFirst()));
        return model;
    }

    @Test
    void terminatesImmediatelyWhenModelReturnsFinalAnswer() {
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(AiMessage.from("done")));
        Agent agent = new Agent(
                modelReturning(scripted),
                List.of(),
                configWithMaxIterations(5),
                Tracer.noop());

        RunResult result = agent.run(Task.of("hi"));

        assertThat(result.completed()).isTrue();
        assertThat(result.iterations()).isEqualTo(1);
        assertThat(result.finalAnswer()).isEqualTo("done");
    }

    @Test
    void invokesToolThenReturnsAnswer(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("hello.txt"), "world");

        ToolExecutionRequest toolReq = ToolExecutionRequest.builder()
                .id("call-1")
                .name("readFile")
                .arguments("{\"path\":\"hello.txt\"}")
                .build();
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(
                AiMessage.from(List.of(toolReq)),
                AiMessage.from("the file says: world")));

        ChatModel model = modelReturning(scripted);
        Agent agent = new Agent(
                model,
                List.of(new FileTools(root)),
                configWithMaxIterations(5),
                Tracer.noop());

        RunResult result = agent.run(new Task("read hello.txt", root));

        assertThat(result.completed()).isTrue();
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(result.finalAnswer()).contains("world");
        verify(model, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void enforcesMaxIterations(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("hello.txt"), "x");

        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            ToolExecutionRequest req = ToolExecutionRequest.builder()
                    .id("call")
                    .name("readFile")
                    .arguments("{\"path\":\"hello.txt\"}")
                    .build();
            return responseOf(AiMessage.from(List.of(req)));
        });

        Agent agent = new Agent(
                model,
                List.of(new FileTools(root)),
                configWithMaxIterations(3),
                Tracer.noop());

        RunResult result = agent.run(new Task("loop forever", root));

        assertThat(result.completed()).isFalse();
        assertThat(result.stopReason()).isEqualTo(StopReason.MAX_ITERATIONS);
        assertThat(result.iterations()).isEqualTo(3);
        verify(model, times(3)).chat(any(ChatRequest.class));
    }

    @Test
    void returnsErrorWhenModelThrows() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("network down"));

        Agent agent = new Agent(
                model,
                List.of(),
                configWithMaxIterations(5),
                Tracer.noop());

        RunResult result = agent.run(Task.of("hi"));

        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
        assertThat(result.finalAnswer()).contains("network down");
    }

    @Test
    void recentOnlyMemoryDropsEarlierHistoryFromTheRequest(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("hello.txt"), "x");

        ToolExecutionRequest read = ToolExecutionRequest.builder()
                .id("c").name("readFile").arguments("{\"path\":\"hello.txt\"}").build();
        // two tool turns, then a final answer -> three model calls
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(
                AiMessage.from(List.of(read)),
                AiMessage.from(List.of(read)),
                AiMessage.from("done")));
        ChatModel model = modelReturning(scripted);

        Agent agent = new Agent(
                model, List.of(new FileTools(root)), configWithMaxIterations(5), Tracer.noop(),
                "sys", Critic.noop(), CritiqueMode.NONE, new EditJournal(),
                ConversationMemory.recentOnly());

        agent.run(new Task("read it", root));

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(model, times(3)).chat(captor.capture());
        List<ChatRequest> requests = captor.getAllValues();

        // 3rd request under amnesiac memory: system + task + only the last
        // tool-call/result pair (4), not the full 6-message transcript.
        List<ChatMessage> third = requests.get(2).messages();
        assertThat(third).hasSize(4);
    }
}
