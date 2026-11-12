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
            if (!rows.isEmpty()) {
                TablePrinter table = new TablePrinter("Event", "Date", "Location", "Capacity", "Total",
                        "Approved", "Pending", "Rejected", "Cancelled", "Remaining");
                for (EventReportRow r : rows) {
                    table.addRow(r.eventName(), r.eventDate(), r.location(), r.capacity(), r.total(),
                            r.approved(), r.pending(), r.rejected(), r.cancelled(), r.remainingSlots());
                }
                table.print();
            }
            logDao.logAction(actor.userId(), "REPORT_GENERATED", "Report generated: Event Registration Report");

            if (!rows.isEmpty() && input.readConfirm("Export this report to a CSV file? (Y/N): ")) {
                File file = exporter.exportCsv(rows);
                System.out.println("Report exported successfully.");
                System.out.println("File: " + file.getAbsolutePath());
                logDao.logAction(actor.userId(), "REPORT_EXPORTED", "Report exported: Event Registration Report (CSV)");
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
                System.out.println("(none)");
            } else {
                TablePrinter table = new TablePrinter("Role", "Status", "Count");
                for (String[] row : users) {
                    table.addRow((Object[]) row);
                }
                table.print();
            }

            System.out.println();
            System.out.println("Events:");
            List<String[]> events = reportDao.countEventsByStatus(actor);
            if (events.isEmpty()) {
                System.out.println("(none)");
            } else {
                TablePrinter table = new TablePrinter("Status", "Count");
                for (String[] row : events) {
                    table.addRow((Object[]) row);
                }
                table.print();
            }

            System.out.println();
            System.out.println("Registrations by status:");
            List<String[]> registrations = reportDao.countRegistrationsByStatus(actor);
            if (registrations.isEmpty()) {
                System.out.println("(none)");
            } else {
                TablePrinter table = new TablePrinter("Status", "Count");
                for (String[] row : registrations) {
                    table.addRow((Object[]) row);
                }
                table.print();
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
            if (!rows.isEmpty()) {
                TablePrinter table = new TablePrinter("ID", "Event", "Approved", "Capacity", "% Full",
                        "Available", "Status");
                for (EventReportRow r : rows) {
                    int percent = r.capacity() > 0 ? (r.approved() * 100) / r.capacity() : 0;
                    String status = r.remainingSlots() > 0 ? "AVAILABLE" : "FULL";
                    table.addRow(r.eventId(), r.eventName(), r.approved(), r.capacity(), percent + "%",
                            r.remainingSlots(), status);
                }
                table.print();
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        }
    }
}