package com.scc.ui;

import com.scc.dao.EventDao;
import com.scc.model.User;
import java.sql.SQLException;

/** The Event Staff dashboard: staff handle registrations but cannot create events or manage users. */
public class StaffMenu {
    private final InputHelper input;
    private final EventBrowser browser;
    private final RegistrationMenu registrationMenu;

    public StaffMenu(InputHelper input) {
        this.input = input;
        this.browser = new EventBrowser(input, new EventDao());
        this.registrationMenu = new RegistrationMenu(input);
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("EVENT STAFF DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. View Events");
            System.out.println("2. View Pending Registrations");
            System.out.println("3. Approve Registration");
            System.out.println("4. Reject Registration");
            System.out.println("5. Update Registration");
            System.out.println("6. View Participant List");
            System.out.println("7. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);

            try {
                switch (choice) {
                    case 1:
                        browser.viewEvents(user);
                        break;
                    case 2:
                        registrationMenu.pendingRegistrations(user);
                        break;
                    case 3:
                        registrationMenu.approve(user);
                        break;
                    case 4:
                        registrationMenu.reject(user);
                        break;
                    case 5:
                        registrationMenu.update(user);
                        break;
                    case 6:
                        registrationMenu.participantList(user);
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
}