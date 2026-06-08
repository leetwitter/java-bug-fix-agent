package com.example.calc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AverageTest {

    @Test
    void averageOfThreeNumbers() {
        assertEquals(4.0, Average.compute(new int[]{2, 4, 6}), 1e-9);
    }

    @Test
    void averageOfSingleNumber() {
        assertEquals(5.0, Average.compute(new int[]{5}), 1e-9);
    }

    @Test
    void averageOfEvenSpread() {
        assertEquals(10.0, Average.compute(new int[]{5, 10, 15}), 1e-9);
    }

    @Test
    void rejectsEmptyArray() {
        assertThrows(IllegalArgumentException.class, () -> Average.compute(new int[]{}));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Average.compute(null));
    }
}
