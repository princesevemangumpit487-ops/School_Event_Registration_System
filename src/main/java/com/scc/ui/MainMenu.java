package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;

/**
 * The first screen: 1. Login  2. Register  3. Exit.
 * After a successful login it opens the dashboard that matches the user's role.
 */
public class MainMenu {
    private final InputHelper input;
    private final UserDao userDao = new UserDao();
    private final AccountForm form;

    public MainMenu(InputHelper input) {
        this.input = input;
        this.form = new AccountForm(input, userDao);
    }

    public void run() {
        boolean running = true;
        while (running) {
            InputHelper.header("SCHOOL EVENT REGISTRATION SYSTEM");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 3);

            try {
                switch (choice) {
                    case 1:
                        login();
                        break;
                    case 2:
                        register();
                        break;
                    default:
                        running = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            }
        }
        System.out.println("Thank you for using the School Event Registration System. Goodbye!");
    }

    // ---------- Register ----------

    // The user chooses a role. The new account is PENDING until an admin approves it.
    private void register() throws SQLException {
        InputHelper.header("REGISTER");
        System.out.println("Register as:");
        System.out.println("1. Student");
        System.out.println("2. Event Staff");
        System.out.println("3. Admin");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
        if (choice == 4) {
            return;
        }
        String role = choice == 1 ? User.ROLE_STUDENT : (choice == 2 ? User.ROLE_STAFF : User.ROLE_ADMIN);

        String fullName = input.readNonEmpty("Full Name: ");
        String contact = form.readContact("Contact Number: ");
        String studentNumber = null;
        String course = null;
        if (role.equals(User.ROLE_STUDENT)) { // only students have a student number and a course
            studentNumber = form.readNewStudentNumber("Student Number (example: 2026-001): ");
            course = input.readNonEmpty("Course: ");
        }
        String username = form.readNewUsername();
        String password = form.readNewPassword();

        User created = userDao.register(fullName, contact, username, password, role, studentNumber, course);
        System.out.println();
        System.out.println("Registration successful! Account status: " + created.accountStatus());
        if (created.isPending()) {
            System.out.println("An admin must approve your account before you can log in.");
        } else {
            System.out.println("You are the first admin, so your account was activated automatically.");
            System.out.println("You can now log in as " + username + ".");
        }
    }

    // ---------- Login and logout ----------

    private void login() throws SQLException {
        InputHelper.header("LOGIN");
        String username = input.readNonEmpty("Username: ");
        String password = input.readNonEmpty("Password: ");

        User user = userDao.authenticate(username, password);
        // The same message is used for a wrong username and a wrong password.
        if (user == null) {
            System.out.println("Invalid username or password.");
            return;
        }
        if (user.isPending()) {
            System.out.println("Your account is still PENDING. An admin must approve it before you can log in.");
            return;
        }
        if (!user.isActive()) {
            System.out.println("This account is inactive. Please contact the administrator.");
            return;
        }

        userDao.recordLogin(user);
        System.out.println("Login successful.");
        openDashboard(user);

        userDao.recordLogout(user);
        System.out.println("You have been logged out.");
    }

    // Each role has its own dashboard with different features.
    private void openDashboard(User user) {
        if (user.isAdmin()) {
            new AdminMenu(input).run(user);
        } else if (user.isStaff()) {
            new StaffMenu(input).run(user);
        } else {
            new StudentMenu(input).run(user);
        }
    }
}