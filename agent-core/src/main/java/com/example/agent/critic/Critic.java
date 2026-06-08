package com.example.agent.critic;

/**
 * Reviews a repair agent's proposed fix and returns a verdict.
 *
 * <p>Interface at a seam so the loop can run with a no-op, a real LLM critic,
 * or a mock in tests. A {@code Critic} is part of the <em>repair system</em>,
 * never part of the evaluation harness: it must only ever see what a developer
 * fixing the bug without the answer key would see. Note the method signature
 * deliberately has no parameter through which a benchmark reference solution
 * or held-out grading suite could be passed — preventing oracle leakage at the
 * type level.
 */
public interface Critic {

    /**
     * @param task            the original repair task / prompt
     * @param changedCodeView current on-disk content of the files the repair
     *                        agent modified (a read-only snapshot)
     * @param lastTestOutput  the public test output the repair agent itself
     *                        obtained from its own {@code runTests} call
     * @return a verdict; {@link Critique#approve()} to accept, otherwise
     *         {@link Critique#revise(String)} with feedback
     */
    Critique review(String task, String changedCodeView, String lastTestOutput);

    /** A critic that approves everything — equivalent to having no critic. */
    static Critic noop() {
        return (task, code, tests) -> Critique.approve();
    }
}
