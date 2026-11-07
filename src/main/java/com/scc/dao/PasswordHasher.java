package com.scc.dao;

/**
 * Passwords are stored exactly as typed (no encryption or hashing), as required by the instructor.
 * The class and column names (password_hash) are kept so the rest of the program does not change.
 * hash() returns the password unchanged, and verify() compares the two texts.
 */
public class PasswordHasher {

    public static String hash(String password) {
        return password;
    }

    public static boolean verify(String password, String stored) {
        return stored != null && password != null && stored.equals(password);
    }
}
