package com.example.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PalindromeTest {

    @Test
    void evenLengthPalindrome() {
        assertTrue(Palindrome.isPalindrome("abba"));
    }

    @Test
    void oddLengthPalindrome() {
        assertTrue(Palindrome.isPalindrome("aba"));
    }

    @Test
    void ignoresLeadingCapital() {
        assertTrue(Palindrome.isPalindrome("Aba"));
    }

    @Test
    void ignoresMixedCase() {
        assertTrue(Palindrome.isPalindrome("RaceCar"));
    }

    @Test
    void rejectsNonPalindrome() {
        assertFalse(Palindrome.isPalindrome("hello"));
    }

    @Test
    void emptyStringIsPalindrome() {
        assertTrue(Palindrome.isPalindrome(""));
    }

    @Test
    void singleCharIsPalindrome() {
        assertTrue(Palindrome.isPalindrome("x"));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Palindrome.isPalindrome(null));
    }
}
