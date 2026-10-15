package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.DatabaseConnection;
import com.scc.dao.ReportDao;
import com.scc.model.EventReportRow;
import com.scc.model.User;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Admin screens: Event Registration Report (with CSV export) and System Overview (capacity monitor). */
public class ReportMenu {
    private final InputHelper input;
    private final ReportDao reportDao = new ReportDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final ReportExporter exporter = new ReportExporter();

    public ReportMenu(InputHelper input) {
        this.input = input;
    }

    // ---------- Event Registration Report ----------

    public void viewReport(User actor) {
        InputHelper.header("EVENT REGISTRATION REPORT");
        try {
            List<EventReportRow> rows = reportDao.buildEventReport(actor);
            if (rows.isEmpty()) {
                System.out.println("No events to report.");
            }
            for (EventReportRow r : rows) {
                System.out.println("Event: " + r.eventName());
                System.out.println("Date: " + r.eventDate());
                System.out.println("Location: " + r.location());
                System.out.println("Capacity: " + r.capacity());
                System.out.println();
                System.out.println("Total Registrations: " + r.total());
                System.out.println();
                System.out.println("Approved : " + r.approved());
                System.out.println("Pending  : " + r.pending());
                System.out.println("Rejected : " + r.rejected());
                System.out.println("Cancelled: " + r.cancelled());
                System.out.println();
                System.out.println("Remaining Slots: " + r.remainingSlots());
                InputHelper.printDivider();
            }
            logDao.addLog(actor.userId(), "Report generated: Event Registration Report", null, null);

            if (!rows.isEmpty() && input.readConfirm("Export this report to a CSV file? (Y/N): ")) {
                File file = exporter.exportCsv(rows);
                System.out.println("Report exported successfully.");
                System.out.println("File: " + file.getAbsolutePath());
                logDao.addLog(actor.userId(), "Report exported: Event Registration Report (CSV)", null, null);
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        } catch (IOException e) {
            System.out.println("The report could not be exported: " + e.getMessage());
        }
    }

    // ---------- System overview ----------

    public void viewSystemOverview(User actor) {
        InputHelper.header("SYSTEM OVERVIEW");
        try {
            System.out.println("Database file: " + DatabaseConnection.getDatabasePath()
                    + " (" + DatabaseConnection.getDatabaseFileSize() + " bytes)");

            System.out.println();
            System.out.println("User accounts:");
            List<String[]> users = reportDao.countUsersByRoleAndStatus(actor);
            if (users.isEmpty()) {
                System.out.println("  (none)");
            }
            for (String[] row : users) {
                System.out.println(String.format("  %-9s %-9s %s", row[0], row[1], row[2]));
            }

            System.out.println();
            System.out.println("Events:");
            List<String[]> events = reportDao.countEventsByStatus(actor);
            if (events.isEmpty()) {
                System.out.println("  (none)");
            }
            for (String[] row : events) {
                System.out.println(String.format("  %-9s %s", row[0], row[1]));
            }

            System.out.println();
            System.out.println("Registrations by status:");
            List<String[]> registrations = reportDao.countRegistrationsByStatus(actor);
            if (registrations.isEmpty()) {
                System.out.println("  (none)");
            }
            for (String[] row : registrations) {
                System.out.println(String.format("  %-10s %s", row[0], row[1]));
            }

            System.out.println();
            String[] logs = reportDao.logSummary(actor);
            System.out.println("Activity log entries: " + logs[0] + (logs[1] == null ? "" : " (latest: " + logs[1] + ")"));

            // Capacity monitor: how full is each event?
            InputHelper.header("EVENT CAPACITY MONITOR");
            List<EventReportRow> rows = reportDao.buildEventReport(actor);
            if (rows.isEmpty()) {
                System.out.println("No events found.");
            }
            for (EventReportRow r : rows) {
                int percent = r.capacity() > 0 ? (r.approved() * 100) / r.capacity() : 0;
                String status = r.remainingSlots() > 0 ? "AVAILABLE" : "FULL";
                System.out.println(r.eventName() + " (ID " + r.eventId() + ")");
                System.out.println("  Approved: " + r.approved() + " / " + r.capacity() + " (" + percent
                        + "% full)  Available Slots: " + r.remainingSlots() + "  [" + status + "]");
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        }
    }
}