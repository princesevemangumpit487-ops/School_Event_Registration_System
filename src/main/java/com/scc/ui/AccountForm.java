package com.scc.ui;

import com.scc.dao.UserDao;
import java.sql.SQLException;

/** Input forms with validation for account details (used by Register, Admin, and Student screens). */
public class AccountForm {
    private final InputHelper input;
    private final UserDao userDao;

    public AccountForm(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
    }

    public String readContact(String prompt) {
        String contact = input.readNonEmpty(prompt);
        while (!contact.matches("[0-9+\\- ]{7,20}")) {
            System.out.println("Invalid contact number. Use 7-20 digits (you may also use + - and spaces).");
            contact = input.readNonEmpty(prompt);
        }
        return contact;
    }

    // Asks until the username is valid and not used yet.
    public String readNewUsername() throws SQLException {
        while (true) {
            String username = input.readNonEmpty("Username (3-20 letters, numbers, or underscore): ");
            if (!username.matches("[A-Za-z0-9_]{3,20}")) {
                System.out.println("Invalid username. Use only letters, numbers, and underscore (3-20 characters).");
            } else if (userDao.usernameExists(username)) {
                System.out.println("That username is already taken. Please choose another one.");
            } else {
                return username;
            }
        }
    }

    // Asks until the student number is valid and not used yet (example: 2026-001).
    public String readNewStudentNumber(String prompt) throws SQLException {
        while (true) {
            String number = input.readNonEmpty(prompt);
            if (!number.matches("[A-Za-z0-9\\-]{3,20}")) {
                System.out.println("Invalid student number. Use letters, numbers, and dashes (example: 2026-001).");
            } else if (userDao.studentNumberExists(number)) {
                System.out.println("That student number is already registered.");
            } else {
                return number;
            }
        }
    }

    // Password of exactly 4 characters (letters or numbers), typed twice.
    public static final int PASSWORD_LENGTH = 4;

    public String readNewPassword() {
        while (true) {
            String password = input.readNonEmpty("Password (exactly " + PASSWORD_LENGTH + " letters or numbers): ");
            if (!password.matches("[A-Za-z0-9]{" + PASSWORD_LENGTH + "}")) {
                System.out.println("Invalid password. Use exactly " + PASSWORD_LENGTH + " letters or numbers (no spaces or symbols).");
                continue;
            }
            String confirm = input.readNonEmpty("Confirm Password: ");
            if (confirm.equals(password)) {
                return password;
            }
            System.out.println("The passwords do not match. Please try again.");
        }
    }
}