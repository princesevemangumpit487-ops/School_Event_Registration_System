package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.model.ActivityLog;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The admin's Registration Activity Log viewer (read only). */
public class LogMenu {
    private final InputHelper input;
    private final ActivityLogDao logDao = new ActivityLogDao();

    public LogMenu(InputHelper input) {
        this.input = input;
    }

    public void run(User actor) {
        boolean back = false;
        while (!back) {
            InputHelper.header("REGISTRATION ACTIVITY LOG");
            System.out.println("1. View Recent Activity");
            System.out.println("2. Search by Username");
            System.out.println("3. Search by Event ID");
            System.out.println("4. Search by Registration ID");
            System.out.println("5. Filter by Action Keyword");
            System.out.println("6. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 6);
            try {
                switch (choice) {
                    case 1:
                        show(logDao.findRecent(actor));
                        break;
                    case 2:
                        String username = input.readNonEmpty("Enter username (or part of it): ");
                        show(logDao.findByUsername(actor, username));
                        break;
                    case 3:
                        int eventId = input.readInt("Enter Event ID: ");
                        show(logDao.findByEventId(actor, eventId));
                        break;
                    case 4:
                        int registrationId = input.readInt("Enter Registration ID: ");
                        show(logDao.findByRegistrationId(actor, registrationId));
                        break;
                    case 5:
                        String keyword = input.readNonEmpty("Enter action keyword (example: approved, login, event): ");
                        show(logDao.findByAction(actor, keyword));
                        break;
                    default:
                        back = true;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    private void show(List<ActivityLog> logs) {
        System.out.println();
        if (logs.isEmpty()) {
            System.out.println("No activity records found.");
            return;
        }
        System.out.println(String.format("%-6s %-19s %-12s %-9s %-6s %-6s %s",
                "LogID", "Date and Time", "User", "Role", "Event", "Reg", "Action"));
        InputHelper.printDivider();
        for (ActivityLog log : logs) {
            String event = log.eventId() == null ? "-" : String.valueOf(log.eventId());
            String reg = log.registrationId() == null ? "-" : String.valueOf(log.registrationId());
            System.out.println(String.format("%-6d %-19s %-12s %-9s %-6s %-6s %s",
                    log.logId(), log.timestamp(), log.username(), log.userRole(), event, reg, log.action()));
        }
        InputHelper.printDivider();
        System.out.println("Records shown: " + logs.size() + " (most recent first, up to 100)");
    }
}