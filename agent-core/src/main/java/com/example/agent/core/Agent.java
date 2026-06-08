package com.example.agent.core;

import com.example.agent.critic.Critic;
import com.example.agent.critic.Critique;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.observability.Tracer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.DefaultToolExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reason→act→observe loop. Given a {@link Task}, the agent sends the
 * prompt + registered tool specs to the chat model, executes any tool calls
 * the model requests, feeds the results back, and repeats until the model
 * stops requesting tools (success) or the iteration cap is hit.
 *
 * <p>Phase 1 keeps memory and self-critique out of scope; Phase 4 will wire
 * those in behind {@code AblationConfig} toggles.
 */
public class Agent {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an autonomous Java code-repair assistant.
            Use the provided tools to inspect the project before answering.
            Be concise. When you have enough information to answer the user's
            question, reply directly without calling any more tools.
            """;

    /** Max times a {@link Critic} may bounce a fix back before we accept it,
     *  to prevent the critic and repair agent from ping-ponging forever. */
    private static final int MAX_CRITIQUE_ROUNDS = 2;

    private final ChatModel model;
    private final AgentConfig config;
    private final Tracer tracer;
    private final List<ToolSpecification> toolSpecs;
    private final Map<String, Object> toolObjectsByName;
    private final String systemPrompt;
    private final Critic critic;
    private final CritiqueMode critiqueMode;
    private final EditJournal editJournal;

    public Agent(ChatModel model, List<Object> toolHolders, AgentConfig config, Tracer tracer) {
        this(model, toolHolders, config, tracer, DEFAULT_SYSTEM_PROMPT);
    }

    public Agent(ChatModel model,
                 List<Object> toolHolders,
                 AgentConfig config,
                 Tracer tracer,
                 String systemPrompt) {
        this(model, toolHolders, config, tracer, systemPrompt,
                Critic.noop(), CritiqueMode.NONE, new EditJournal());
    }

    public Agent(ChatModel model,
                 List<Object> toolHolders,
                 AgentConfig config,
                 Tracer tracer,
                 String systemPrompt,
                 Critic critic,
                 CritiqueMode critiqueMode,
                 EditJournal editJournal) {
        this.model = Objects.requireNonNull(model, "model");
        this.config = Objects.requireNonNull(config, "config");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
        this.critic = Objects.requireNonNull(critic, "critic");
        this.critiqueMode = Objects.requireNonNull(critiqueMode, "critiqueMode");
        this.editJournal = Objects.requireNonNull(editJournal, "editJournal");

        this.toolSpecs = new ArrayList<>();
        this.toolObjectsByName = new HashMap<>();
        for (Object holder : toolHolders) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(holder);
            for (ToolSpecification spec : specs) {
                if (toolObjectsByName.containsKey(spec.name())) {
                    throw new IllegalArgumentException("Duplicate tool name: " + spec.name());
                }
                toolSpecs.add(spec);
                toolObjectsByName.put(spec.name(), holder);
            }
        }
    }

    public RunResult run(Task task) {
        Objects.requireNonNull(task, "task");
        tracer.taskStart(task);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(task.prompt()));

        String lastTestOutput = "(the author did not run the tests)";
        int critiqueRounds = 0;

        int iteration;
        for (iteration = 0; iteration < config.maxIterations(); iteration++) {
            tracer.iterationStart(iteration);

            ChatRequest.Builder requestBuilder = ChatRequest.builder().messages(messages);
            if (!toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }

            ChatResponse response;
            try {
                response = model.chat(requestBuilder.build());
            } catch (RuntimeException e) {
                RunResult error = RunResult.error("model error: " + e.getMessage(), iteration);
                tracer.taskEnd(error);
                return error;
            }

            AiMessage aiMessage = response.aiMessage();
            messages.add(aiMessage);
            tracer.modelResponse(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                if (critiqueMode == CritiqueMode.CRITIC
                        && critiqueRounds < MAX_CRITIQUE_ROUNDS
                        && !editJournal.isEmpty()) {
                    Critique critique;
                    try {
                        critique = critic.review(
                                task.prompt(), editJournal.snapshot(), lastTestOutput);
                    } catch (RuntimeException e) {
                        // Fail open: a critic whose own LLM call throws must never
                        // crash the repair loop (the exception would otherwise escape
                        // run() uncaught) nor deadlock it. Treat the error as an
                        // approval so the proposed fix is accepted rather than lost.
                        critique = Critique.approve();
                    }
                    tracer.critique(critique);
                    if (!critique.approved()) {
                        critiqueRounds++;
                        messages.add(UserMessage.from(
                                "A code reviewer inspected your change and rejected it. "
                                + "Address this feedback, then finish:\n" + critique.feedback()));
                        continue;
                    }
                }
                RunResult done = RunResult.completed(aiMessage.text(), iteration + 1);
                tracer.taskEnd(done);
                return done;
            }

            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                String result = executeTool(request);
                if ("runTests".equals(request.name())) {
                    lastTestOutput = result;
                }
                messages.add(ToolExecutionResultMessage.from(request, result));
                tracer.toolCall(request.name(), request.arguments(), result);
            }
        }

        RunResult capped = RunResult.maxIterations(iteration);
        tracer.taskEnd(capped);
        return capped;
    }

    private String executeTool(ToolExecutionRequest request) {
        Object holder = toolObjectsByName.get(request.name());
        if (holder == null) {
            return "Error: unknown tool '" + request.name() + "'.";
        }
        try {
            return new DefaultToolExecutor(holder, request).execute(request, null);
        } catch (RuntimeException e) {
            return "Error: tool '" + request.name() + "' failed: " + e.getMessage();
        }
    }
}
