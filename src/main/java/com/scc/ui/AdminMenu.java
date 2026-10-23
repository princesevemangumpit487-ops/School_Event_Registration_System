package com.scc.ui;

import com.scc.dao.RegistrationDao;
import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;

/** The Admin dashboard: events, users, all registrations, report, activity log, and system overview. */
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
            System.out.println("3. View All Registrations");
            System.out.println("4. Event Registration Report");
            System.out.println("5. Registration Activity Logs");
            System.out.println("6. System Overview");
            System.out.println("7. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);

            try {
                switch (choice) {
                    case 1:
                        eventMenu.run(user);
                        break;
                    case 2:
                        userMenu.run(user);
                        break;
                    case 3:
                        viewAllRegistrations(user);
                        break;
                    case 4:
                        reportMenu.viewReport(user);
                        break;
                    case 5:
                        logMenu.run(user);
                        break;
                    case 6:
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