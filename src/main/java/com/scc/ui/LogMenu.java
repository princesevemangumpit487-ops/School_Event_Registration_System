package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.model.ActivityLog;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The admin's System Audit Log viewer (read only). */
public class LogMenu {
    private static final int DESCRIPTION_WIDTH = 35;

    private final InputHelper input;
    private final ActivityLogDao logDao = new ActivityLogDao();

    public LogMenu(InputHelper input) {
        this.input = input;
    }

    public void run(User actor) {
        boolean back = false;
        while (!back) {
            InputHelper.header("SYSTEM AUDIT LOGS");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Search by Username");
            System.out.println("4. Search by Event ID");
            System.out.println("5. Search by Registration ID");
            System.out.println("6. Filter by Action Keyword");
            System.out.println("7. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);
            try {
                switch (choice) {
                    case 1:
                        show(logDao.getAllLogs());
                        break;
                    case 2:
                        int userId = input.readInt("Enter User ID: ");
                        show(logDao.getLogsByUserId(userId));
                        break;
                    case 3:
                        String username = input.readNonEmpty("Enter username (or part of it): ");
                        show(logDao.findByUsername(actor, username));
                        break;
                    case 4:
                        int eventId = input.readInt("Enter Event ID: ");
                        show(logDao.findByEventId(actor, eventId));
                        break;
                    case 5:
                        int registrationId = input.readInt("Enter Registration ID: ");
                        show(logDao.findByRegistrationId(actor, registrationId));
                        break;
                    case 6:
                        String keyword = input.readNonEmpty("Enter keyword (example: USER_APPROVED, approved, login): ");
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

    // Aligned table. A long description continues on the next line(s) so nothing is cut off.
    private void show(List<ActivityLog> logs) {
        System.out.println();
        if (logs.isEmpty()) {
            System.out.println("No activity records found.");
            return;
        }
        TablePrinter table = new TablePrinter("ID", "User ID", "Action Type", "Description", "Timestamp")
                .maxWidth(3, DESCRIPTION_WIDTH);
        for (ActivityLog log : logs) {
            String description = log.description();
            if (log.eventId() != null && !description.contains("Event #" + log.eventId())) {
                description += " [Event #" + log.eventId() + "]";
            }
            if (log.registrationId() != null && !description.contains("Registration #" + log.registrationId())) {
                description += " [Reg #" + log.registrationId() + "]";
            }
            table.addRow(log.logId(), log.userId(), log.actionType(), description, log.timestamp());
        }
        table.print();
        System.out.println("Records shown: " + logs.size() + " (most recent first)");
    }


}