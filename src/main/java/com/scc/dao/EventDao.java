package com.scc.dao;

import com.scc.model.Event;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for school events. Everybody who is logged in can read events;
 * only the Admin can create, update, or remove them. Events are never deleted (removing sets REMOVED).
 */
public class EventDao {
    // approved_count is calculated by the database, so it is always correct.
    private static final String SELECT_EVENT =
            "SELECT e.event_id, e.event_name, e.event_date, e.location, e.capacity, e.event_status, "
                    + "(SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.event_id AND r.status = 'Approved') AS approved_count "
                    + "FROM events e";

    private final ActivityLogDao logDao = new ActivityLogDao();

    // ---------- Create ----------

    public int create(User admin, String name, String date, String location, int capacity) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        }
        String sql = "INSERT INTO events (event_name, event_date, location, capacity, event_status) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name);
                    ps.setString(2, date);
                    ps.setString(3, location);
                    ps.setInt(4, capacity);
                    ps.executeUpdate();
                }
                int eventId = DatabaseConnection.lastInsertId(conn);
                logDao.addLog(conn, admin.userId(), "Created event: " + name, eventId, null);
                conn.commit();
                return eventId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // ---------- Read (any logged-in user) ----------

    public List<Event> findActiveEvents() throws SQLException {
        return query(" WHERE e.event_status = 'ACTIVE' ORDER BY e.event_id");
    }

    public List<Event> searchByName(String keyword) throws SQLException {
        return query(" WHERE e.event_status = 'ACTIVE' AND e.event_name LIKE ? ORDER BY e.event_id", "%" + keyword + "%");
    }

    public List<Event> searchByDate(String keyword) throws SQLException {
        return query(" WHERE e.event_status = 'ACTIVE' AND e.event_date LIKE ? ORDER BY e.event_id", "%" + keyword + "%");
    }

    // Returns the event only if it exists and was not removed.
    public Event findActiveById(int eventId) throws SQLException {
        List<Event> found = query(" WHERE e.event_id = ? AND e.event_status = 'ACTIVE'", eventId);
        return found.isEmpty() ? null : found.get(0);
    }

    // ---------- Update (Admin) ----------

    public boolean updateName(User admin, Event event, String value) throws SQLException {
        return updateText(admin, event, "event_name", value, "name");
    }

    public boolean updateDate(User admin, Event event, String value) throws SQLException {
        return updateText(admin, event, "event_date", value, "date");
    }

    public boolean updateLocation(User admin, Event event, String value) throws SQLException {
        return updateText(admin, event, "location", value, "location");
    }

    // The column name is always written in the three methods above, never typed by a user.
    private boolean updateText(User admin, Event event, String column, String value, String what) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        String sql = "UPDATE events SET " + column + " = ? WHERE event_id = ? AND event_status = 'ACTIVE'";
        return updateAndLog(admin, event.eventId(), sql, new Object[] {value, event.eventId()},
                "Updated event: " + event.eventName() + " (" + what + " updated)");
    }

    // Changes the capacity, but NEVER below the number of approved participants (checked by the database).
    // Returns false when the new capacity is too low.
    public boolean updateCapacity(User admin, Event event, int newCapacity) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        if (newCapacity <= 0) {
            return false;
        }
        String sql = "UPDATE events SET capacity = ? WHERE event_id = ? AND event_status = 'ACTIVE' "
                + "AND (SELECT COUNT(*) FROM registrations r WHERE r.event_id = events.event_id "
                + "AND r.status = 'Approved') <= ?";
        return updateAndLog(admin, event.eventId(), sql, new Object[] {newCapacity, event.eventId(), newCapacity},
                "Updated event: " + event.eventName() + " (capacity changed to " + newCapacity + ")");
    }

    // ---------- Remove (Admin, soft delete) ----------

    public boolean remove(User admin, Event event) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        String sql = "UPDATE events SET event_status = 'REMOVED' WHERE event_id = ? AND event_status = 'ACTIVE'";
        return updateAndLog(admin, event.eventId(), sql, new Object[] {event.eventId()},
                "Removed event: " + event.eventName());
    }

    // ---------- Helpers ----------

    // Runs one UPDATE and writes its log in one transaction. Returns false if no row was changed.
    private boolean updateAndLog(User admin, int eventId, String sql, Object[] params, String logText) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bind(ps, params);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, admin.userId(), logText, eventId, null);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Integer) {
                ps.setInt(i + 1, (Integer) params[i]);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }

    // "suffix" is written in this file; user values are only passed as parameters.
    private List<Event> query(String suffix, Object... params) throws SQLException {
        List<Event> events = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_EVENT + suffix)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new Event(rs.getInt("event_id"), rs.getString("event_name"),
                            rs.getString("event_date"), rs.getString("location"), rs.getInt("capacity"),
                            rs.getString("event_status"), rs.getInt("approved_count")));
                }
            }
        }
        return events;
    }
}