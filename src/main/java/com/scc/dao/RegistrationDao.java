package com.scc.dao;

import com.scc.model.Event;
import com.scc.model.RegisterResult;
import com.scc.model.Registration;
import com.scc.model.StatusResult;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for registrations. Students only reach their own registrations.
 * A student's cancellation never deletes the record: it changes the status to Cancelled.
 * The capacity rule is checked INSIDE the SQL statement, so an event can never go over its capacity.
 */
public class RegistrationDao {
    private static final String SELECT_REG =
            "SELECT r.registration_id, r.student_id, u.full_name, u.student_number, u.course, r.event_id, "
                    + "e.event_name, e.event_date, e.location, r.registration_date, r.status "
                    + "FROM registrations r JOIN users u ON r.student_id = u.user_id JOIN events e ON r.event_id = e.event_id";

    private final ActivityLogDao logDao = new ActivityLogDao();
    private final EventDao eventDao = new EventDao();

    // =====================================================================
    // STUDENT
    // =====================================================================

    // True if the student already has a Pending or Approved registration for this event.
    public boolean hasActiveRegistration(int studentId, int eventId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return hasActiveRegistration(conn, studentId, eventId);
        }
    }

    private boolean hasActiveRegistration(Connection conn, int studentId, int eventId) throws SQLException {
        String sql = "SELECT 1 FROM registrations WHERE student_id = ? AND event_id = ? "
                + "AND status IN ('Pending', 'Approved')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Registers the logged-in student. The new registration starts as Pending.
    // It checks: the event exists, no duplicate registration, and the event is not full.
    public RegisterResult register(User student, int eventId) throws SQLException {
        Permissions.require(student, User.ROLE_STUDENT);
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String eventName;
                int capacity;
                int approved;
                String eventSql = "SELECT event_name, capacity, (SELECT COUNT(*) FROM registrations r "
                        + "WHERE r.event_id = events.event_id AND r.status = 'Approved') "
                        + "FROM events WHERE event_id = ? AND event_status = 'ACTIVE'";
                try (PreparedStatement ps = conn.prepareStatement(eventSql)) {
                    ps.setInt(1, eventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new RegisterResult(RegisterResult.Outcome.EVENT_NOT_FOUND, 0);
                        }
                        eventName = rs.getString(1);
                        capacity = rs.getInt(2);
                        approved = rs.getInt(3);
                    }
                }
                if (hasActiveRegistration(conn, student.userId(), eventId)) {
                    conn.rollback();
                    return new RegisterResult(RegisterResult.Outcome.ALREADY_REGISTERED, 0);
                }
                if (approved >= capacity) { // availableSlots = capacity - approvedCount
                    conn.rollback();
                    return new RegisterResult(RegisterResult.Outcome.EVENT_FULL, 0);
                }

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO registrations "
                        + "(student_id, event_id, registration_date, status) VALUES (?, ?, ?, 'Pending')")) {
                    ps.setInt(1, student.userId());
                    ps.setInt(2, eventId);
                    ps.setString(3, java.time.LocalDate.now().toString());
                    ps.executeUpdate();
                }
                int registrationId = DatabaseConnection.lastInsertId(conn);
                logDao.addLog(conn, student.userId(), "Registered for " + eventName, eventId, registrationId);
                conn.commit();
                return new RegisterResult(RegisterResult.Outcome.REGISTERED, registrationId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // The student's own registrations. "My Registrations" hides Cancelled ones; "History" shows everything.
    public List<Registration> findStudentRegistrations(User student, boolean includeCancelled) throws SQLException {
        Permissions.require(student, User.ROLE_STUDENT);
        String suffix = " WHERE r.student_id = ?" + (includeCancelled ? "" : " AND r.status <> 'Cancelled'")
                + " ORDER BY r.registration_id DESC";
        return query(suffix, student.userId());
    }

    // Only finds the registration if it belongs to this student.
    public Registration findStudentRegistration(User student, int registrationId) throws SQLException {
        Permissions.require(student, User.ROLE_STUDENT);
        List<Registration> found = query(" WHERE r.registration_id = ? AND r.student_id = ?",
                registrationId, student.userId());
        return found.isEmpty() ? null : found.get(0);
    }

    // Cancels the student's own registration, only while it is Pending. The record is kept.
    public boolean cancelOwn(User student, int registrationId) throws SQLException {
        Permissions.require(student, User.ROLE_STUDENT);
        Registration reg = findStudentRegistration(student, registrationId);
        if (reg == null) {
            return false;
        }
        return setStatusIfCurrent(student.userId(), reg, Registration.STATUS_PENDING,
                Registration.STATUS_CANCELLED, "Cancelled registration for " + reg.eventName());
    }

    // =====================================================================
    // STAFF AND ADMIN (reading)
    // =====================================================================

    public Registration findById(User actor, int registrationId) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        List<Registration> found = query(" WHERE r.registration_id = ?", registrationId);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Registration> findPending(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        return query(" WHERE r.status = 'Pending' ORDER BY r.registration_id");
    }

    public List<Registration> findAll(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" ORDER BY r.registration_id");
    }

    // The participant list of one event: ONLY Approved registrations.
    public List<Registration> findApprovedByEvent(User actor, int eventId) throws SQLException {
        Permissions.require(actor, User.ROLE_STAFF, User.ROLE_ADMIN);
        return query(" WHERE r.event_id = ? AND r.status = 'Approved' ORDER BY u.full_name", eventId);
    }

    // =====================================================================
    // STAFF (updating)
    // =====================================================================

    // Approves a Pending registration ONLY if the event still has a free slot.
    // The capacity check is part of the UPDATE statement itself (atomic).
    public StatusResult approve(User staff, int registrationId) throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        Registration reg = firstOrNull(query(" WHERE r.registration_id = ?", registrationId));
        if (reg == null) {
            return StatusResult.NOT_FOUND;
        }
        if (!reg.status().equals(Registration.STATUS_PENDING)) {
            return StatusResult.INVALID_CHANGE;
        }
        String sql = "UPDATE registrations SET status = 'Approved' WHERE registration_id = ? AND status = 'Pending' "
                + "AND EXISTS (SELECT 1 FROM events e WHERE e.event_id = registrations.event_id "
                + "AND e.event_status = 'ACTIVE' "
                + "AND (SELECT COUNT(*) FROM registrations x WHERE x.event_id = e.event_id AND x.status = 'Approved') < e.capacity)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, registrationId);
                    rows = ps.executeUpdate();
                }
                if (rows == 1) {
                    logDao.addLog(conn, staff.userId(), "Approved registration for " + reg.eventName(),
                            reg.eventId(), registrationId);
                    conn.commit();
                    return StatusResult.CHANGED;
                }
                conn.rollback();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
        // Nothing was changed: find out why.
        Event event = eventDao.findActiveById(reg.eventId());
        if (event == null) {
            return StatusResult.EVENT_REMOVED;
        }
        return event.isFull() ? StatusResult.EVENT_FULL : StatusResult.ALREADY_CHANGED;
    }

    // Rejects a Pending registration.
    public StatusResult reject(User staff, int registrationId) throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        Registration reg = firstOrNull(query(" WHERE r.registration_id = ?", registrationId));
        if (reg == null) {
            return StatusResult.NOT_FOUND;
        }
        if (!reg.status().equals(Registration.STATUS_PENDING)) {
            return StatusResult.INVALID_CHANGE;
        }
        boolean saved = setStatusIfCurrent(staff.userId(), reg, Registration.STATUS_PENDING,
                Registration.STATUS_REJECTED, "Rejected registration for " + reg.eventName());
        return saved ? StatusResult.CHANGED : StatusResult.ALREADY_CHANGED;
    }

    // Changes the status following the allowed changes (see Registration.isValidChange).
    public StatusResult changeStatus(User staff, int registrationId, String newStatus) throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        Registration reg = firstOrNull(query(" WHERE r.registration_id = ?", registrationId));
        if (reg == null) {
            return StatusResult.NOT_FOUND;
        }
        if (!Registration.isValidChange(reg.status(), newStatus)) {
            return StatusResult.INVALID_CHANGE;
        }
        if (newStatus.equals(Registration.STATUS_APPROVED)) {
            return approve(staff, registrationId); // includes the capacity check
        }
        if (newStatus.equals(Registration.STATUS_PENDING)) {
            if (eventDao.findActiveById(reg.eventId()) == null) {
                return StatusResult.EVENT_REMOVED;
            }
            if (hasActiveRegistration(reg.studentId(), reg.eventId())) {
                return StatusResult.DUPLICATE; // the student registered again after the rejection
            }
        }
        boolean saved = setStatusIfCurrent(staff.userId(), reg, reg.status(), newStatus,
                "Changed registration status from " + reg.status() + " to " + newStatus + " (" + reg.eventName() + ")");
        return saved ? StatusResult.CHANGED : StatusResult.ALREADY_CHANGED;
    }

    // Staff changes the registration date (the text must be a valid date, checked by the screen).
    public boolean updateDate(User staff, int registrationId, String newDate) throws SQLException {
        Permissions.require(staff, User.ROLE_STAFF);
        Registration reg = firstOrNull(query(" WHERE r.registration_id = ?", registrationId));
        if (reg == null) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE registrations SET registration_date = ? WHERE registration_id = ?")) {
                    ps.setString(1, newDate);
                    ps.setInt(2, registrationId);
                    ps.executeUpdate();
                }
                logDao.addLog(conn, staff.userId(), "Updated registration information (date)",
                        reg.eventId(), registrationId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =====================================================================
    // ADMIN (removing an invalid record)
    // =====================================================================

    // The only real DELETE in the system: the admin removes an invalid registration record.
    // The log is written first; the database then clears the registration link of older log rows.
    public boolean remove(User admin, int registrationId) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        Registration reg = firstOrNull(query(" WHERE r.registration_id = ?", registrationId));
        if (reg == null) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                logDao.addLog(conn, admin.userId(), "Removed invalid registration record (ID " + registrationId + ", "
                        + reg.studentName() + ", " + reg.eventName() + ")", reg.eventId(), null);
                int rows;
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM registrations WHERE registration_id = ?")) {
                    ps.setInt(1, registrationId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    // Changes the status and writes the log in one transaction.
    // "AND status = ?" makes sure the registration still has the status that was shown on screen.
    private boolean setStatusIfCurrent(int actorId, Registration reg, String oldStatus, String newStatus,
                                       String logText) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE registrations SET status = ? WHERE registration_id = ? AND status = ?")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, reg.registrationId());
                    ps.setString(3, oldStatus);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, actorId, logText, reg.eventId(), reg.registrationId());
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private Registration firstOrNull(List<Registration> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    // "suffix" is written in this file; user values are only passed as parameters.
    private List<Registration> query(String suffix, Object... params) throws SQLException {
        List<Registration> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_REG + suffix)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, (Integer) params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Registration(rs.getInt("registration_id"), rs.getInt("student_id"),
                            rs.getString("full_name"), rs.getString("student_number"), rs.getString("course"),
                            rs.getInt("event_id"), rs.getString("event_name"), rs.getString("event_date"),
                            rs.getString("location"), rs.getString("registration_date"), rs.getString("status")));
                }
            }
        }
        return list;
    }
}