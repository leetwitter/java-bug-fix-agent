package com.example.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GcdTest {

    @Test
    void coprimeNumbersHaveGcdOne() {
        assertEquals(1, Gcd.gcd(7, 1));
    }

    @Test
    void sharedFactor() {
        assertEquals(4, Gcd.gcd(12, 8));
    }

    @Test
    void largerInputs() {
        assertEquals(6, Gcd.gcd(54, 24));
    }

    @Test
    void zeroOnTheLeft() {
        assertEquals(5, Gcd.gcd(0, 5));
    }

    @Test
    void zeroOnTheRight() {
        assertEquals(5, Gcd.gcd(5, 0));
    }

    @Test
    void negativesAreNormalized() {
        assertEquals(4, Gcd.gcd(-12, 8));
    }
}
