package com.example.agent.critic;

/**
 * How the repair loop reviews a fix before declaring success. This is the
 * "self-critique" axis of the Phase 4 ablation matrix.
 *
 * <ul>
 *   <li>{@link #NONE} — accept as soon as the model stops calling tools (the
 *       original behaviour; prone to false positives).</li>
 *   <li>{@link #SELF} — the repair model critiques its own work. Reserved for
 *       Phase 4; currently behaves like {@code NONE}.</li>
 *   <li>{@link #CRITIC} — a separate {@link Critic} agent reviews the fix and
 *       can bounce it back for another round.</li>
 * </ul>
 */
public enum CritiqueMode {
    NONE,
    SELF,
    CRITIC;

    public static CritiqueMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.trim().toLowerCase()) {
            case "self" -> SELF;
            case "critic" -> CRITIC;
            default -> NONE;
        };
    }
}
