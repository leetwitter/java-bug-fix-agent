package com.example.calc;

public final class Average {

    private Average() {}

    public static double compute(int[] xs) {
        if (xs == null || xs.length == 0) {
            throw new IllegalArgumentException("empty input");
        }
        long sum = 0;
        for (int x : xs) sum += x;
        return (double) sum / (xs.length - 1);
    }
}
