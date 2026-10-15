package com.scc.dao;

import com.scc.model.EventReportRow;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the Event Registration Report and the admin's System Overview. Admin only. */
public class ReportDao {

    // One row per active event. The database counts the registrations of each status.
    public List<EventReportRow> buildEventReport(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        String sql = "SELECT e.event_id, e.event_name, e.event_date, e.location, e.capacity, "
                + "COALESCE(SUM(CASE WHEN r.status = 'Approved' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.status = 'Pending' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.status = 'Rejected' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.status = 'Cancelled' THEN 1 ELSE 0 END), 0) "
                + "FROM events e LEFT JOIN registrations r ON r.event_id = e.event_id "
                + "WHERE e.event_status = 'ACTIVE' GROUP BY e.event_id ORDER BY e.event_id";
        List<EventReportRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new EventReportRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9)));
            }
        }
        return rows;
    }

    // Rows of {role, status, count}
    public List<String[]> countUsersByRoleAndStatus(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT role, account_status, COUNT(*) FROM users GROUP BY role, account_status "
                + "ORDER BY role, account_status", 3);
    }

    // Rows of {status, count}
    public List<String[]> countRegistrationsByStatus(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT status, COUNT(*) FROM registrations GROUP BY status ORDER BY status", 2);
    }

    // Rows of {status, count} for events (ACTIVE / REMOVED)
    public List<String[]> countEventsByStatus(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT event_status, COUNT(*) FROM events GROUP BY event_status ORDER BY event_status", 2);
    }

    // {number of log entries, latest log time (or null)}
    public String[] logSummary(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return rows("SELECT COUNT(*), MAX(timestamp) FROM activity_logs", 2).get(0);
    }

    private List<String[]> rows(String sql, int columns) throws SQLException {
        List<String[]> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = rs.getString(i + 1);
                }
                result.add(row);
            }
        }
        return result;
    }
}