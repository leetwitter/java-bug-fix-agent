package com.example.agent.cli;

import com.example.agent.assembly.BugfixAgentFactory;
import com.example.agent.assembly.BugfixAgentFactory.Assembled;
import com.example.agent.core.AgentConfig;
import com.example.agent.core.RunResult;
import com.example.agent.core.Task;
import com.example.agent.critic.CritiqueMode;
import com.example.agent.observability.Tracer;

import java.nio.file.Files;
import java.nio.file.Path;

public final class App {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: agent <projectPath> [<prompt>]");
            System.exit(64);
            return;
        }

        Path projectRoot = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectRoot)) {
            System.err.println("Error: not a directory: " + projectRoot);
            System.exit(66);
            return;
        }

        String prompt = args.length >= 2 ? args[1] : BugfixAgentFactory.DEFAULT_BUGFIX_PROMPT;

        AgentConfig config = AgentConfig.fromEnv();
        CritiqueMode critiqueMode = CritiqueMode.fromString(System.getenv("AGENT_CRITIQUE"));
        String criticModel = System.getenv("AGENT_CRITIC_MODEL");
        System.out.println("[cfg] provider=" + config.provider()
                + " model=" + config.modelName()
                + " maxIterations=" + config.maxIterations()
                + " critique=" + critiqueMode
                + " root=" + projectRoot);

        long t0 = System.currentTimeMillis();
        Assembled assembled = BugfixAgentFactory.assemble(
                projectRoot, config, critiqueMode, criticModel, Tracer.console());
        long indexMs = System.currentTimeMillis() - t0;
        System.out.println("[init] indexed " + assembled.symbolCount() + " symbols in " + indexMs + "ms");
        if (assembled.hasTestRunner()) {
            System.out.println("[init] test runner: " + assembled.testRunnerName());
        } else {
            System.out.println("[init] no test runner — runTests tool disabled");
        }
        if (critiqueMode == CritiqueMode.CRITIC) {
            System.out.println("[init] critic model: "
                    + ((criticModel == null || criticModel.isBlank()) ? config.modelName() : criticModel));
        }

        RunResult result = assembled.agent().run(new Task(prompt, projectRoot));

        System.out.println();
        System.out.println("=== Result ===");
        System.out.println("stopReason: " + result.stopReason());
        System.out.println("iterations: " + result.iterations());
        if (result.finalAnswer() != null) {
            System.out.println("answer:");
            System.out.println(result.finalAnswer());
        }
        System.exit(result.completed() ? 0 : 1);
    }
}
