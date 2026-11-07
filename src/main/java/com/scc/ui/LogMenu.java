package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.model.ActivityLog;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** The admin's System Audit Log viewer (read only). */
public class LogMenu {
    private static final int DESCRIPTION_WIDTH = 35;
    private static final String ROW_FORMAT = "%-5s | %-8s | %-18s | %-35s | %-20s";
    private static final String DIVIDER = "-".repeat(98); // width of one table row

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
        System.out.println(String.format(ROW_FORMAT, "ID", "User ID", "Action Type", "Description", "Timestamp"));
        System.out.println(DIVIDER);
        for (ActivityLog log : logs) {
            String description = log.description();
            if (log.eventId() != null && !description.contains("Event #" + log.eventId())) {
                description += " [Event #" + log.eventId() + "]";
            }
            if (log.registrationId() != null && !description.contains("Registration #" + log.registrationId())) {
                description += " [Reg #" + log.registrationId() + "]";
            }
            List<String> lines = wrap(description, DESCRIPTION_WIDTH);
            for (int i = 0; i < lines.size(); i++) {
                if (i == 0) {
                    System.out.println(String.format(ROW_FORMAT, log.logId(), log.userId(), log.actionType(),
                            lines.get(i), log.timestamp()));
                } else {
                    System.out.println(String.format(ROW_FORMAT, "", "", "", lines.get(i), ""));
                }
            }
        }
        System.out.println(DIVIDER);
        System.out.println("Records shown: " + logs.size() + " (most recent first)");
    }

    // Splits text into lines of at most "width" characters, breaking at spaces when possible.
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > width) { // a single very long word is cut
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
