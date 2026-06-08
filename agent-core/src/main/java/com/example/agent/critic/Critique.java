package com.example.agent.critic;

/**
 * A critic's verdict on the repair agent's proposed fix.
 *
 * <p>{@code approved == true} means the fix may be accepted as final;
 * otherwise {@link #feedback()} carries concrete, actionable problems to
 * feed back to the repair agent for another round.
 */
public record Critique(boolean approved, String feedback) {

    public static Critique approve() {
        return new Critique(true, "");
    }

    public static Critique revise(String feedback) {
        return new Critique(false, feedback == null ? "" : feedback);
    }
}
