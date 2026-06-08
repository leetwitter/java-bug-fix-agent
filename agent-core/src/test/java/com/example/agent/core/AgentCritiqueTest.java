package com.example.agent.core;

import com.example.agent.critic.Critic;
import com.example.agent.critic.Critique;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.observability.Tracer;
import com.example.agent.tools.FileTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCritiqueTest {

    private static AgentConfig config(int maxIterations) {
        return new AgentConfig("ollama", "test-model", "http://localhost", 0.0, maxIterations, null);
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

    private static ToolExecutionRequest writeReq() {
        return ToolExecutionRequest.builder()
                .id("w1")
                .name("writeFile")
                .arguments("{\"path\":\"Average.java\",\"content\":\"package x; class Average {}\"}")
                .build();
    }

    /** A critic that returns scripted verdicts and counts how often it is called. */
    private static final class ScriptedCritic implements Critic {
        final Deque<Critique> verdicts;
        int calls = 0;

        ScriptedCritic(Critique... v) {
            this.verdicts = new ArrayDeque<>(List.of(v));
        }

        @Override
        public Critique review(String task, String code, String tests) {
            calls++;
            Critique next = verdicts.pollFirst();
            return next != null ? next : Critique.approve();
        }
    }

    @Test
    void rejectedFixTriggersAnotherRoundThenApproval(@TempDir Path root) {
        EditJournal journal = new EditJournal();
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(
                AiMessage.from(List.of(writeReq())),  // iter0: edit a file
                AiMessage.from("done"),               // iter1: tries to finish -> rejected
                AiMessage.from("done v2")));           // iter2: finishes -> approved

        ScriptedCritic critic = new ScriptedCritic(Critique.revise("missing package"), Critique.approve());

        Agent agent = new Agent(
                modelReturning(scripted),
                List.of(new FileTools(root, journal)),
                config(5),
                Tracer.noop(),
                "sys",
                critic,
                CritiqueMode.CRITIC,
                journal);

        RunResult result = agent.run(new Task("fix it", root));

        assertThat(result.completed()).isTrue();
        assertThat(result.finalAnswer()).isEqualTo("done v2");
        assertThat(result.iterations()).isEqualTo(3);
        assertThat(critic.calls).isEqualTo(2);
    }

    @Test
    void critiqueRoundsAreCappedSoTheLoopCannotPingPongForever(@TempDir Path root) {
        EditJournal journal = new EditJournal();
        ChatModel model = mock(ChatModel.class);
        AtomicInteger n = new AtomicInteger();
        when(model.chat(any(ChatRequest.class))).thenAnswer(inv ->
                n.getAndIncrement() == 0
                        ? responseOf(AiMessage.from(List.of(writeReq())))  // first: edit
                        : responseOf(AiMessage.from("done")));             // then: always tries to finish

        ScriptedCritic critic = new ScriptedCritic(
                Critique.revise("nope"), Critique.revise("still nope"), Critique.revise("again"));

        Agent agent = new Agent(model, List.of(new FileTools(root, journal)), config(10),
                Tracer.noop(), "sys", critic, CritiqueMode.CRITIC, journal);

        RunResult result = agent.run(new Task("fix it", root));

        // After MAX_CRITIQUE_ROUNDS (2) rejections, the loop accepts and stops.
        assertThat(result.completed()).isTrue();
        assertThat(critic.calls).isEqualTo(2);
    }

    @Test
    void noneModeNeverInvokesTheCritic(@TempDir Path root) {
        EditJournal journal = new EditJournal();
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(
                AiMessage.from(List.of(writeReq())),
                AiMessage.from("done")));
        ScriptedCritic critic = new ScriptedCritic();

        Agent agent = new Agent(modelReturning(scripted), List.of(new FileTools(root, journal)),
                config(5), Tracer.noop(), "sys", critic, CritiqueMode.NONE, journal);

        RunResult result = agent.run(new Task("fix it", root));

        assertThat(result.completed()).isTrue();
        assertThat(critic.calls).isEqualTo(0);
    }

    @Test
    void aCriticThatThrowsFailsOpenInsteadOfCrashingTheLoop(@TempDir Path root) {
        // Guards the latent hazard: critic.review() is the critic's own LLM call,
        // and if it throws (timeout, transport error) the exception must not escape
        // run(). Fail-open => the fix is accepted and the loop completes normally.
        EditJournal journal = new EditJournal();
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(
                AiMessage.from(List.of(writeReq())),  // iter0: edit a file
                AiMessage.from("done")));             // iter1: tries to finish -> critic blows up
        Critic exploding = (task, code, tests) -> {
            throw new RuntimeException("critic LLM unreachable");
        };

        Agent agent = new Agent(modelReturning(scripted), List.of(new FileTools(root, journal)),
                config(5), Tracer.noop(), "sys", exploding, CritiqueMode.CRITIC, journal);

        RunResult result = agent.run(new Task("fix it", root));

        assertThat(result.completed()).isTrue();
        assertThat(result.finalAnswer()).isEqualTo("done");
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test
    void critiqueIsSkippedWhenNoFilesWereChanged(@TempDir Path root) {
        // No writeFile -> nothing to review -> the critic must not run.
        EditJournal journal = new EditJournal();
        Deque<AiMessage> scripted = new ArrayDeque<>(List.of(AiMessage.from("just answering")));
        ScriptedCritic critic = new ScriptedCritic();

        Agent agent = new Agent(modelReturning(scripted), List.of(new FileTools(root, journal)),
                config(5), Tracer.noop(), "sys", critic, CritiqueMode.CRITIC, journal);

        RunResult result = agent.run(new Task("question", root));

        assertThat(result.completed()).isTrue();
        assertThat(critic.calls).isEqualTo(0);
    }
}
