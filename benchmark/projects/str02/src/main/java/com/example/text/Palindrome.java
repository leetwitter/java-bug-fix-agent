package com.example.text;

public final class Palindrome {

    private Palindrome() {}

    /**
     * Returns true if {@code s} reads the same forwards and backwards,
     * ignoring letter case (so "Aba" and "RaceCar" are palindromes).
     */
    public static boolean isPalindrome(String s) {
        if (s == null) {
            throw new IllegalArgumentException("null input");
        }
        int i = 0;
        int j = s.length() - 1;
        while (i < j) {
            if (s.charAt(i) != s.charAt(j)) {
                return false;
            }
            i++;
            j--;
        }
        return true;
    }
}
