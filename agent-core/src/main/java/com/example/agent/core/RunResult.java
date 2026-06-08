package com.example.agent.core;

public record RunResult(StopReason stopReason, String finalAnswer, int iterations) {

    public boolean completed() {
        return stopReason == StopReason.COMPLETED;
    }

    public static RunResult completed(String finalAnswer, int iterations) {
        return new RunResult(StopReason.COMPLETED, finalAnswer, iterations);
    }

    public static RunResult maxIterations(int iterations) {
        return new RunResult(StopReason.MAX_ITERATIONS, null, iterations);
    }

    public static RunResult error(String message, int iterations) {
        return new RunResult(StopReason.ERROR, message, iterations);
    }
}
