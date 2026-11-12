package com.scc.dao;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashes passwords before they are saved, so the database never holds the real password.
 * Uses PBKDF2 with a random salt (built into Java, no extra library needed).
 * Stored format: pbkdf2$iterations$salt$hash
 *
 * Old accounts that still have a plain-text password (for example "123") can still log in:
 * verify() falls back to a plain comparison when the stored value is not in the hashed format.
 * Those accounts become hashed the next time their password is changed or reset.
 */
public class PasswordHasher {
    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    // Turns a password into a salted hash. The same password gives a different result each time.
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    // Checks a typed password against the stored value.
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (!isHashed(stored)) {
            return stored.equals(password); // old plain-text account
        }
        String[] parts = stored.split("\\$");
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
            return java.security.MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$") && stored.split("\\$").length == 4;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is not available.", e);
        }
    }
}