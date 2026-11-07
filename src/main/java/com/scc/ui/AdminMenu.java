package com.scc.ui;

import com.scc.dao.RegistrationDao;
import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The Admin dashboard: events, users, role / status updates, all registrations, report, audit logs, and system overview. */
public class AdminMenu {
    private final InputHelper input;
    private final UserDao userDao;
    private final RegistrationDao registrationDao = new RegistrationDao();
    private final EventMenu eventMenu;
    private final UserMenu userMenu;
    private final ReportMenu reportMenu;
    private final LogMenu logMenu;

    public AdminMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.eventMenu = new EventMenu(input);
        this.userMenu = new UserMenu(input, userDao);
        this.reportMenu = new ReportMenu(input);
        this.logMenu = new LogMenu(input);
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("ADMIN DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            showPendingNotice(user);
            System.out.println();
            System.out.println("1. Manage Events");
            System.out.println("2. Manage Users");
            System.out.println("3. Update User Role and Status");
            System.out.println("4. View All Registrations");
            System.out.println("5. Event Registration Report");
            System.out.println("6. View System Audit Logs");
            System.out.println("7. System Overview");
            System.out.println("8. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 8);

            try {
                switch (choice) {
                    case 1:
                        eventMenu.run(user);
                        break;
                    case 2:
                        userMenu.run(user);
                        break;
                    case 3:
                        updateUserRoleAndStatus(user);
                        break;
                    case 4:
                        viewAllRegistrations(user);
                        break;
                    case 5:
                        reportMenu.viewReport(user);
                        break;
                    case 6:
                        logMenu.run(user);
                        break;
                    case 7:
                        reportMenu.viewSystemOverview(user);
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    // ---------- Update a user's role and status ----------

    // Shows all users, asks for the target User ID, then the new role and status.
    // Entering 0 at any step cancels. The admin cannot change their own (active) account.
    private void updateUserRoleAndStatus(User admin) throws SQLException {
        InputHelper.header("UPDATE USER ROLE AND STATUS");
        List<User> users = userDao.findAll(admin, null, null, null);
        userMenu.showUsers(users);
        System.out.println("(Enter 0 at any step to cancel.)");

        User target = null;
        while (target == null) {
            int targetUserId = readIntInput("Enter the User ID to update: ");
            if (targetUserId == 0) {
                System.out.println("Update cancelled.");
                return;
            }
            if (targetUserId == admin.userId()) {
                System.out.println("WARNING: Modifying the currently active admin account is restricted.");
                continue;
            }
            target = userDao.findById(targetUserId);
            if (target == null) {
                System.out.println("No account found with that ID. Please try again.");
            }
        }
        System.out.println("Selected: " + target.username() + " (" + target.fullName() + ") - current role "
                + target.role() + ", current status " + target.accountStatus());

        String newRole = readValidRole();
        if (newRole == null) {
            System.out.println("Update cancelled.");
            return;
        }
        String newStatus = readValidStatus();
        if (newStatus == null) {
            System.out.println("Update cancelled.");
            return;
        }
        if (!confirmUpdate(target, newRole, newStatus)) {
            System.out.println("Update cancelled. No changes were made.");
            return;
        }
        if (userDao.updateUserRoleAndStatus(admin, target.userId(), newRole, newStatus)) {
            System.out.println("Account updated: " + target.username() + " is now " + newRole + " / " + newStatus + ".");
        } else {
            System.out.println("The account no longer exists. No changes were made.");
        }
    }

    // Final check before saving. Y saves; N or 0 cancels.
    private boolean confirmUpdate(User target, String newRole, String newStatus) {
        System.out.println("Change " + target.username() + ": role " + target.role() + " -> " + newRole
                + ", status " + target.accountStatus() + " -> " + newStatus);
        while (true) {
            String answer = input.readLine("Save this change? (Y/N, 0 to cancel): ");
            if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("YES")) {
                return true;
            } else if (answer.equalsIgnoreCase("N") || answer.equalsIgnoreCase("NO") || answer.equals("0")) {
                return false;
            }
            System.out.println("Please type Y, N, or 0.");
        }
    }

    // ---------- Input validation wrappers ----------

    // Keeps asking until the user types a whole number. Letters and symbols do not crash the program.
    private int readIntInput(String prompt) {
        while (true) {
            String text = input.readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    // Keeps asking until a valid role is typed (STUDENT, STAFF, ADMIN). Returns null when 0 is entered.
    private String readValidRole() {
        while (true) {
            String text = input.readLine("New role (STUDENT, STAFF, ADMIN, or 0 to cancel): ").toUpperCase();
            if (text.equals("0")) {
                return null;
            }
            if (text.equals(User.ROLE_STUDENT) || text.equals(User.ROLE_STAFF) || text.equals(User.ROLE_ADMIN)) {
                return text;
            }
            System.out.println("Invalid role. Please type STUDENT, STAFF, or ADMIN.");
        }
    }

    // Keeps asking until a valid status is typed. APPROVED is accepted and saved as ACTIVE (the database name for it).
    // Returns null when 0 is entered.
    private String readValidStatus() {
        while (true) {
            String text = input.readLine("New status (APPROVED, PENDING, INACTIVE, or 0 to cancel): ").toUpperCase();
            if (text.equals("0")) {
                return null;
            }
            if (text.equals("APPROVED") || text.equals(User.STATUS_ACTIVE)) {
                return User.STATUS_ACTIVE;
            }
            if (text.equals(User.STATUS_PENDING) || text.equals(User.STATUS_INACTIVE)) {
                return text;
            }
            System.out.println("Invalid status. Please type APPROVED, PENDING, or INACTIVE.");
        }
    }

    // Lists every registration. The admin can then remove an invalid record (the only real delete in the system).
    private void viewAllRegistrations(User admin) throws SQLException {
        InputHelper.header("ALL REGISTRATIONS");
        var all = registrationDao.findAll(admin);
        RegistrationView.printForStaff(all);
        if (all.isEmpty() || !input.readConfirm("Remove an invalid registration record? (Y/N): ")) {
            return;
        }
        int id = input.readInt("Enter Registration ID to remove: ");
        var reg = registrationDao.findById(admin, id);
        if (reg == null) {
            System.out.println("Registration not found.");
            return;
        }
        System.out.println("Student: " + reg.studentName() + " | Event: " + reg.eventName() + " | Status: " + reg.status());
        if (!input.readConfirm("Permanently remove this invalid registration record? (Y/N): ")) {
            System.out.println("Registration was not removed.");
            return;
        }
        if (registrationDao.remove(admin, id)) {
            System.out.println("Registration record removed.");
        } else {
            System.out.println("The registration could not be removed.");
        }
    }

    // Reminds the admin when new accounts are waiting for approval.
    private void showPendingNotice(User admin) {
        try {
            int pending = userDao.countPendingAccounts(admin);
            if (pending > 0) {
                System.out.println("Notice: " + pending + " account(s) are waiting for your approval.");
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        }
    }
}