package com.example.agent.critic;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Objects;

/**
 * An LLM-backed {@link Critic}. Makes a single chat call with a strict text
 * contract: the reply's first non-blank line is {@code VERDICT: APPROVE} or
 * {@code VERDICT: REVISE}, the rest is feedback. A text contract is used rather
 * than a structured tool call because the verdict is low-stakes and we want to
 * fail open — an unparseable reply approves rather than blocking the loop.
 */
public final class LlmCritic implements Critic {

    private static final String SYSTEM_PROMPT = """
            You are a strict senior Java code reviewer checking a proposed bug fix.
            You did NOT write this code and you do NOT have the reference solution.
            Judge ONLY from the material shown to you.

            Reject the change (REVISE) if you see any of:
              - a missing `package` declaration or missing imports,
              - a partial, truncated, or non-compilable file,
              - an off-by-one, sign, or boundary error,
              - a claim of success that the shown test output does not support
                (e.g. the test output is not clearly green).

            Respond in EXACTLY this format and nothing else:
            VERDICT: APPROVE
            or
            VERDICT: REVISE
            <one or two sentences of concrete, actionable feedback>

            The first line MUST start with "VERDICT:".
            """;

    private final ChatModel model;

    public LlmCritic(ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public Critique review(String task, String changedCodeView, String lastTestOutput) {
        String user = """
                ## Task given to the author
                %s

                ## Files the author changed (current content)
                %s

                ## Test output the author last obtained
                %s
                """.formatted(
                nullToNone(task),
                nullToNone(changedCodeView),
                nullToNone(lastTestOutput));

        ChatResponse resp = model.chat(ChatRequest.builder()
                .messages(List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(user)))
                .build());

        return parse(resp.aiMessage().text());
    }

    /**
     * Parses a critic reply. Fails open: any reply whose first non-blank line
     * is not a recognizable {@code VERDICT: REVISE} is treated as approval, so
     * a confused critic never deadlocks the repair loop.
     */
    static Critique parse(String text) {
        if (text == null || text.isBlank()) {
            return Critique.approve();
        }
        List<String> lines = text.stripLeading().lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase();
            if (upper.startsWith("VERDICT:")) {
                String verdict = upper.substring("VERDICT:".length()).strip();
                if (verdict.startsWith("REVISE")) {
                    String inlineFeedback = line.substring(line.toUpperCase().indexOf("REVISE") + "REVISE".length()).strip();
                    String rest = String.join("\n", lines.subList(i + 1, lines.size())).strip();
                    String feedback = (inlineFeedback + "\n" + rest).strip();
                    return Critique.revise(feedback.isBlank() ? "Reviewer requested changes." : feedback);
                }
                return Critique.approve();
            }
            // first meaningful line wasn't a verdict -> fail open
            return Critique.approve();
        }
        return Critique.approve();
    }

    private static String nullToNone(String s) {
        return (s == null || s.isBlank()) ? "(none)" : s;
    }
}
