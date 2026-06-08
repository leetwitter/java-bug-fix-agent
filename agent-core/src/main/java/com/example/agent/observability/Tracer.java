package com.example.agent.observability;

import com.example.agent.core.RunResult;
import com.example.agent.core.Task;
import com.example.agent.critic.Critique;
import dev.langchain4j.data.message.AiMessage;

/**
 * Records every step the agent takes. Phase 1 ships a no-op and a console
 * implementation; Phase 4 will add a structured JSON-per-run tracer.
 */
public interface Tracer {

    default void taskStart(Task task) {}

    default void iterationStart(int iteration) {}

    default void modelResponse(AiMessage message) {}

    default void toolCall(String toolName, String arguments, String result) {}

    default void critique(Critique critique) {}

    default void taskEnd(RunResult result) {}

    static Tracer noop() {
        return new Tracer() {};
    }

    static Tracer console() {
        return new ConsoleTracer();
    }

    final class ConsoleTracer implements Tracer {

        @Override
        public void taskStart(Task task) {
            System.out.println("[task] start: " + truncate(task.prompt(), 200));
        }

        @Override
        public void iterationStart(int iteration) {
            System.out.println("[iter] " + iteration);
        }

        @Override
        public void modelResponse(AiMessage message) {
            String text = message.text();
            if (text != null && !text.isBlank()) {
                System.out.println("[llm] " + truncate(text, 400));
            }
            if (message.hasToolExecutionRequests()) {
                message.toolExecutionRequests().forEach(r ->
                        System.out.println("[llm] tool-call " + r.name() + " " + truncate(r.arguments(), 200)));
            }
        }

        @Override
        public void toolCall(String toolName, String arguments, String result) {
            System.out.println("[tool] " + toolName + " -> " + truncate(result, 300));
        }

        @Override
        public void critique(Critique critique) {
            System.out.println("[critic] " + (critique.approved()
                    ? "APPROVE"
                    : "REVISE: " + truncate(critique.feedback(), 300)));
        }

        @Override
        public void taskEnd(RunResult result) {
            System.out.println("[task] end: " + result.stopReason()
                    + " iterations=" + result.iterations());
        }

        private static String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() <= max ? s : s.substring(0, max) + "…";
        }
    }
}
