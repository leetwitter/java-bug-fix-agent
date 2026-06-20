package com.example.agent.assembly;

import com.example.agent.core.Agent;
import com.example.agent.core.AgentConfig;
import com.example.agent.core.EditJournal;
import com.example.agent.critic.Critic;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.critic.LlmCritic;
import com.example.agent.exec.TestRunner;
import com.example.agent.memory.ConversationMemory;
import com.example.agent.llm.ChatModelFactory;
import com.example.agent.observability.Tracer;
import com.example.agent.rag.SymbolIndex;
import com.example.agent.rag.SymbolIndexer;
import com.example.agent.rag.SymbolRetriever;
import com.example.agent.tools.FileTools;
import com.example.agent.tools.SearchCodeTool;
import com.example.agent.tools.TestRunnerTool;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a fully-wired bug-fix {@link Agent} for a target project: symbol
 * index, file/search/test tools, model, and (optionally) a critic.
 *
 * <p>Both the CLI ({@code App}) and the benchmark {@code EvalHarness} build the
 * agent through here so the benchmark measures the <em>same</em> agent that runs
 * in production — otherwise the reported resolution rate would describe a
 * different system than the one shipped.
 */
public final class BugfixAgentFactory {

    private BugfixAgentFactory() {}

    public static final String BUGFIX_SYSTEM_PROMPT = """
            You are an autonomous Java code-repair assistant working inside a single project.

            Tools you may call:
              - searchCode(query): locate classes/methods/fields by name without dumping the whole repo.
              - readFile(path): read a file (relative to the project root).
              - writeFile(path, content): OVERWRITE a production file. Writes under src/test/ are BLOCKED.
              - listFiles(path): list a directory.
              - runTests(): run the project's tests; returns pass/fail + failing test names + output tail.

            Recommended workflow:
              1. Call runTests() first to see what's failing.
              2. Use searchCode + readFile to locate the bug.
              3. Edit the PRODUCTION code with writeFile. Keep edits minimal.
              4. Re-run runTests(). If still failing, read the new output and iterate.
              5. When tests pass, produce a brief final answer describing what you changed.

            Hard rules:
              - NEVER modify files under src/test/. The tests are the spec.
              - When you write a file, provide the FULL new content — writeFile overwrites.
              - Stop calling tools and produce a final answer the moment the tests are green.
              - Be concise; do not echo whole files back.
            """;

    public static final String DEFAULT_BUGFIX_PROMPT =
            "There may be a bug in this project. Run the tests; if any fail, find the cause "
            + "in the production code, fix it, and verify the tests pass.";

    /** The assembled agent plus handles the caller may want for logging. */
    public record Assembled(Agent agent, EditJournal journal, int symbolCount, String testRunnerName) {
        public boolean hasTestRunner() {
            return testRunnerName != null;
        }
    }

    /**
     * @param projectRoot      target project to operate on
     * @param config           model/provider/iteration configuration
     * @param critiqueMode     completion-gate mode (NONE/SELF/CRITIC)
     * @param criticModelName  model for the critic when mode is CRITIC; null/blank
     *                         falls back to {@code config.modelName()}
     * @param tracer           observability sink
     */
    public static Assembled assemble(Path projectRoot,
                                     AgentConfig config,
                                     CritiqueMode critiqueMode,
                                     String criticModelName,
                                     Tracer tracer) {
        return assemble(projectRoot, config, critiqueMode, criticModelName, tracer, true);
    }

    /**
     * As {@link #assemble(Path, AgentConfig, CritiqueMode, String, Tracer)} but with
     * the AST retrieval (RAG) axis toggleable for the ablation matrix. Memory stays
     * on (full history) — the production default.
     */
    public static Assembled assemble(Path projectRoot,
                                     AgentConfig config,
                                     CritiqueMode critiqueMode,
                                     String criticModelName,
                                     Tracer tracer,
                                     boolean retrievalEnabled) {
        return assemble(projectRoot, config, critiqueMode, criticModelName, tracer,
                retrievalEnabled, true);
    }

    /**
     * As {@link #assemble(Path, AgentConfig, CritiqueMode, String, Tracer, boolean)}
     * but with the conversation-memory axis toggleable too — the full ablation
     * surface (RAG x memory x critique).
     *
     * @param retrievalEnabled when {@code false}, the symbol index is not built and
     *                         {@code searchCode} is not offered — the agent must
     *                         locate code with {@code listFiles}/{@code readFile}
     *                         alone. This is the RAG-off arm of the ablation.
     * @param memoryEnabled    when {@code false}, the agent is amnesiac: each turn
     *                         it sees only the system prompt, the task, and the most
     *                         recent observation (no reason→act→observe history).
     *                         This is the memory-off arm of the ablation.
     */
    public static Assembled assemble(Path projectRoot,
                                     AgentConfig config,
                                     CritiqueMode critiqueMode,
                                     String criticModelName,
                                     Tracer tracer,
                                     boolean retrievalEnabled,
                                     boolean memoryEnabled) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(config, "config");

        EditJournal journal = new EditJournal();
        List<Object> tools = new ArrayList<>();
        tools.add(new FileTools(projectRoot, journal));

        int symbolCount = 0;
        if (retrievalEnabled) {
            SymbolIndex symbolIndex = new SymbolIndexer().index(projectRoot);
            tools.add(new SearchCodeTool(new SymbolRetriever(symbolIndex)));
            symbolCount = symbolIndex.size();
        }

        String testRunnerName = null;
        try {
            TestRunner runner = TestRunner.forProject(projectRoot);
            tools.add(new TestRunnerTool(runner));
            testRunnerName = runner.getClass().getSimpleName();
        } catch (IllegalArgumentException e) {
            // No recognizable build tool; the runTests tool is simply unavailable.
        }

        ChatModel model = ChatModelFactory.create(config);
        Critic critic = buildCritic(critiqueMode, config, criticModelName);
        ConversationMemory memory =
                memoryEnabled ? ConversationMemory.full() : ConversationMemory.recentOnly();
        Agent agent = new Agent(model, tools, config, tracer, BUGFIX_SYSTEM_PROMPT,
                critic, critiqueMode, journal, memory);

        return new Assembled(agent, journal, symbolCount, testRunnerName);
    }

    private static Critic buildCritic(CritiqueMode mode, AgentConfig config, String criticModelName) {
        if (mode != CritiqueMode.CRITIC) {
            return Critic.noop();
        }
        String criticModel = (criticModelName == null || criticModelName.isBlank())
                ? config.modelName()
                : criticModelName;
        AgentConfig criticConfig = new AgentConfig(
                config.provider(),
                criticModel,
                config.baseUrl(),
                config.temperature(),
                config.maxIterations(),
                config.openAiApiKey());
        return new LlmCritic(ChatModelFactory.create(criticConfig));
    }
}
