package com.example.math;

public final class Gcd {

    private Gcd() {}

    /**
     * Greatest common divisor via the Euclidean algorithm. Operates on the
     * absolute values, so signs are ignored. {@code gcd(0, n) == n} and
     * {@code gcd(n, 0) == n}.
     */
    public static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return b;
    }
}
