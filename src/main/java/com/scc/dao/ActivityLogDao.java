package com.scc.dao;

import com.scc.model.ActivityLog;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the Activity Log (table activity_logs). Logs can only be added or read:
 * there is no method to edit or delete them.
 *
 * Action types used in the system:
 *   USER_LOGIN, USER_LOGOUT, USER_REGISTERED, USER_CREATED, USER_APPROVED, USER_REJECTED, USER_UPDATED,
 *   PROFILE_UPDATED, EVENT_CREATED, EVENT_UPDATED, EVENT_REMOVED, EVENT_REGISTERED, REG_APPROVED, REG_REJECTED,
 *   REG_CANCELLED, REG_STATUS_CHANGED, REG_UPDATED, REG_REMOVED, REPORT_GENERATED, REPORT_EXPORTED
 */
public class ActivityLogDao {
    private static final int MAX_ROWS = 100;

    // ---------- Writing logs ----------

    // Adds a log using its own connection (for actions like login, logout, and reports).
    public void logAction(int userId, String actionType, String description) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            logAction(conn, userId, actionType, description, null, null);
        }
    }

    // Adds a log inside a transaction that is already open (so the data and its log are saved together).
    public void logAction(Connection conn, int userId, String actionType, String description, Integer eventId,
                          Integer registrationId) throws SQLException {
        String sql = "INSERT INTO activity_logs (user_id, event_id, registration_id, action_type, description, "
                + "timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            if (eventId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, eventId);
            }
            if (registrationId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, registrationId);
            }
            ps.setString(4, actionType);
            ps.setString(5, description);
            ps.setString(6, DatabaseConnection.now());
            ps.executeUpdate();
        }
    }

    // ---------- Reading logs (used by the admin audit screen) ----------

    // Every recorded action, newest first.
    public List<ActivityLog> getAllLogs() throws SQLException {
        return query(" ORDER BY l.timestamp DESC, l.id DESC");
    }

    // Every action done by one user, newest first.
    public List<ActivityLog> getLogsByUserId(int userId) throws SQLException {
        return query(" WHERE l.user_id = ? ORDER BY l.timestamp DESC, l.id DESC", userId);
    }

    public List<ActivityLog> findByUsername(User actor, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE u.username LIKE ? ORDER BY l.timestamp DESC, l.id DESC LIMIT " + MAX_ROWS,
                "%" + keyword + "%");
    }

    public List<ActivityLog> findByEventId(User actor, int eventId) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE l.event_id = ? ORDER BY l.timestamp DESC, l.id DESC LIMIT " + MAX_ROWS, eventId);
    }

    public List<ActivityLog> findByRegistrationId(User actor, int registrationId) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE l.registration_id = ? ORDER BY l.timestamp DESC, l.id DESC LIMIT " + MAX_ROWS,
                registrationId);
    }

    // Matches the action type or the description.
    public List<ActivityLog> findByAction(User actor, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE l.action_type LIKE ? OR l.description LIKE ? "
                + "ORDER BY l.timestamp DESC, l.id DESC LIMIT " + MAX_ROWS, "%" + keyword + "%", "%" + keyword + "%");
    }

    // The part after "suffix" is written in this file; user text is only passed as a parameter.
    private List<ActivityLog> query(String suffix, Object... params) throws SQLException {
        String sql = "SELECT l.id, l.user_id, u.username, u.role, l.action_type, l.description, "
                + "l.event_id, l.registration_id, l.timestamp "
                + "FROM activity_logs l LEFT JOIN users u ON l.user_id = u.user_id" + suffix;
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Integer) {
                    ps.setInt(i + 1, (Integer) params[i]);
                } else {
                    ps.setString(i + 1, params[i].toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int eventValue = rs.getInt("event_id");
                    Integer eventId = rs.wasNull() ? null : eventValue;
                    int registrationValue = rs.getInt("registration_id");
                    Integer registrationId = rs.wasNull() ? null : registrationValue;
                    logs.add(new ActivityLog(rs.getInt("id"), rs.getInt("user_id"), rs.getString("username"),
                            rs.getString("role"), rs.getString("action_type"), rs.getString("description"),
                            eventId, registrationId, rs.getString("timestamp")));
                }
            }
        }
        return logs;
    }
}
