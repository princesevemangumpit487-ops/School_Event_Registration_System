package com.scc.dao;

import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the users table: register, login check, and account management.
 * Accounts are never deleted. Their status is changed instead (PENDING, ACTIVE, INACTIVE).
 */
public class UserDao {
    private static final String SELECT_USER = "SELECT user_id, full_name, contact_number, username, "
            + "password_hash, role, account_status, created_at, student_number, course FROM users";

    private final ActivityLogDao logDao = new ActivityLogDao();

    // ---------- Finding users ----------

    public User findByUsername(String username) throws SQLException {
        return findOne(" WHERE username = ?", username);
    }

    public User findById(int userId) throws SQLException {
        return findOne(" WHERE user_id = ?", userId);
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    public boolean studentNumberExists(String studentNumber) throws SQLException {
        return findOne(" WHERE student_number = ?", studentNumber) != null;
    }

    // Admin: accounts filtered by role, status, and/or a name/username keyword (any of them can be null).
    public List<User> findAll(User actor, String role, String status, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        StringBuilder sql = new StringBuilder(SELECT_USER + " WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (role != null) {
            sql.append(" AND role = ?");
            params.add(role);
        }
        if (status != null) {
            sql.append(" AND account_status = ?");
            params.add(status);
        }
        if (keyword != null) {
            sql.append(" AND (full_name LIKE ? OR username LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY user_id");

        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
            }
        }
        return users;
    }

    private User findOne(String where, Object... params) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_USER + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
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

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("contact_number"),
                rs.getString("username"), rs.getString("password_hash"), rs.getString("role"),
                rs.getString("account_status"), rs.getString("created_at"),
                rs.getString("student_number"), rs.getString("course"));
    }

    // ---------- Login and logout ----------

    // Checks the username and password against the database.
    // Returns the user if the password is correct (any status), or null if the login details are wrong.
    public User authenticate(String username, String password) throws SQLException {
        User user = findByUsername(username);
        if (user == null || !PasswordHasher.verify(password, user.passwordHash())) {
            return null;
        }
        return user;
    }

    public void recordLogin(User user) throws SQLException {
        logDao.logAction(user.userId(), "USER_LOGIN", "User " + user.username() + " (ID: " + user.userId() + ") logged in.");
    }

    public void recordLogout(User user) throws SQLException {
        logDao.logAction(user.userId(), "USER_LOGOUT", "User " + user.username() + " (ID: " + user.userId() + ") logged out.");
    }

    // ---------- Register ----------

    // Saves a new account for any of the three roles (STUDENT, STAFF, ADMIN).
    // The status is PENDING until an admin approves it.
    // Exception: while the system has no ACTIVE admin, a new ADMIN account is ACTIVE straight away,
    // so the very first admin can start the system (otherwise nobody could approve anybody).
    // studentNumber and course are only used when the role is STUDENT (pass null otherwise).
    public User register(String fullName, String contact, String username, String password, String role,
                         String studentNumber, String course) throws SQLException {
        if (!User.ROLE_STUDENT.equals(role) && !User.ROLE_STAFF.equals(role) && !User.ROLE_ADMIN.equals(role)) {
            throw new IllegalArgumentException("Unknown role.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String status = User.ROLE_ADMIN.equals(role) && countActiveAdmins(conn) == 0
                        ? User.STATUS_ACTIVE : User.STATUS_PENDING;
                int newId = insertUser(conn, fullName, contact, username, password, role, status, studentNumber, course);
                logDao.logAction(conn, newId, "USER_REGISTERED",
                        "Account registered: " + username + " (" + role + ", " + status + ")", null, null);
                conn.commit();
                return findById(newId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Saves a user row and returns the new user ID. The password is saved exactly as typed.
    private int insertUser(Connection conn, String fullName, String contact, String username, String password,
                           String role, String status, String studentNumber, String course) throws SQLException {
        // Students get their number automatically (example: 2026-001) when none is given.
        if (User.ROLE_STUDENT.equals(role) && (studentNumber == null || studentNumber.isBlank())) {
            studentNumber = nextStudentNumber(conn);
        }
        String sql = "INSERT INTO users (full_name, contact_number, username, password_hash, role, "
                + "account_status, created_at, student_number, course) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, contact);
            ps.setString(3, username);
            ps.setString(4, PasswordHasher.hash(password));
            ps.setString(5, role);
            ps.setString(6, status);
            ps.setString(7, DatabaseConnection.now());
            ps.setString(8, studentNumber);
            ps.setString(9, course);
            ps.executeUpdate();
        }
        return DatabaseConnection.lastInsertId(conn);
    }

    // Next student number for this year: YEAR-001, YEAR-002, ... (continues after the highest one saved).
    private String nextStudentNumber(Connection conn) throws SQLException {
        String prefix = LocalDate.now().getYear() + "-";
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTR(student_number, ?) AS INTEGER)), 0) "
                + "FROM users WHERE student_number LIKE ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, prefix.length() + 1);
            ps.setString(2, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return String.format("%s%03d", prefix, rs.getInt(1) + 1);
            }
        }
    }

    // ---------- Admin: account management ----------

    // Admin creates a Student or Staff account. These are ACTIVE immediately.
    public int createAccount(User actor, String fullName, String contact, String username, String password,
                             String role, String studentNumber, String course) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int newId = insertUser(conn, fullName, contact, username, password, role,
                        User.STATUS_ACTIVE, studentNumber, course);
                logDao.logAction(conn, actor.userId(), "USER_CREATED",
                        "Admin [" + actor.userId() + "] created account: " + username + " (" + role + ")", null, null);
                conn.commit();
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin edits one field. field must be one of: full_name, contact_number, course, student_number.
    public void updateField(User actor, User target, String field, String value) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        if (!field.equals("full_name") && !field.equals("contact_number")
                && !field.equals("course") && !field.equals("student_number")) {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
        saveField(actor.userId(), target.userId(), field, value, "USER_UPDATED",
                "User account edited: " + target.username() + " (" + field.replace('_', ' ') + " updated)");
    }

    // Admin resets the password of an account.
    public void resetPassword(User actor, User target, String newPassword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        saveField(actor.userId(), target.userId(), "password_hash", PasswordHasher.hash(newPassword), "USER_UPDATED",
                "User account edited: " + target.username() + " (password reset)");
    }

    // A student changes their OWN full name, contact number, course, or password.
    // Role, status, and student number cannot be changed here.
    public void updateOwnField(User student, String field, String value) throws SQLException {
        Permissions.require(student, User.ROLE_STUDENT);
        if (!field.equals("full_name") && !field.equals("contact_number")
                && !field.equals("course") && !field.equals("password_hash")) {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
        String what = field.equals("password_hash") ? "password" : field.replace('_', ' ');
        saveField(student.userId(), student.userId(), field, value, "PROFILE_UPDATED",
                "Student profile updated (" + what + ")");
    }

    // Updates ONE column and writes the log in the same transaction.
    // The column name is always checked above, never taken directly from user input.
    private void saveField(int actorId, int targetId, String column, String value, String actionType,
                           String logText) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET " + column + " = ? WHERE user_id = ?")) {
                    ps.setString(1, value);
                    ps.setInt(2, targetId);
                    ps.executeUpdate();
                }
                logDao.logAction(conn, actorId, actionType, logText, null, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin approves, rejects, activates, or deactivates an account.
    // Returns false (and changes nothing) when it would leave the system without an active admin.
    public boolean setStatus(User actor, User target, String newStatus, String actionType, String logText)
            throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (target.isAdmin() && !newStatus.equals(User.STATUS_ACTIVE)
                    && (target.userId() == actor.userId() || countActiveAdmins(conn) <= 1)) {
                return false; // safeguard: keep at least one active admin
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET account_status = ? WHERE user_id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, target.userId());
                    ps.executeUpdate();
                }
                logDao.logAction(conn, actor.userId(), actionType, logText, null, null);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin approves a PENDING account (status becomes ACTIVE) and logs USER_APPROVED.
    public boolean approveUser(User actor, User target) throws SQLException {
        return setStatus(actor, target, User.STATUS_ACTIVE, "USER_APPROVED",
                "Admin [" + actor.userId() + "] approved User [" + target.userId() + "]");
    }

    // Admin changes the role and the account status of an account in one step.
    // Both values are converted to UPPERCASE before they are sent to the database.
    // This overload also checks the actor is an admin, refuses to change the actor's own account,
    // and writes activity logs (saved together with the update).
    // Returns false when no account with that ID exists.
    public boolean updateUserRoleAndStatus(User actor, int userId, String newRole, String newStatus)
            throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        if (actor.userId() == userId) {
            throw new IllegalArgumentException("Modifying the currently active admin account is restricted.");
        }
        return saveRoleAndStatus(actor.userId(), userId, newRole, newStatus);
    }

    // Same update without an actor (no permission check and no logs). Use the overload above from menus.
    // Runs: UPDATE users SET role = ?, account_status = ? WHERE user_id = ?
    // (the "roles / status / id" of the requirement are role / account_status / user_id in this database).
    public boolean updateUserRoleAndStatus(int userId, String newRole, String newStatus) throws SQLException {
        return saveRoleAndStatus(null, userId, newRole, newStatus);
    }

    private boolean saveRoleAndStatus(Integer actorId, int userId, String newRole, String newStatus)
            throws SQLException {
        if (newRole == null || newStatus == null) {
            throw new IllegalArgumentException("Role and status are required.");
        }
        String role = newRole.trim().toUpperCase();
        String status = newStatus.trim().toUpperCase();
        if (!role.equals(User.ROLE_STUDENT) && !role.equals(User.ROLE_STAFF) && !role.equals(User.ROLE_ADMIN)) {
            throw new IllegalArgumentException("Unknown role.");
        }
        if (!status.equals(User.STATUS_PENDING) && !status.equals(User.STATUS_ACTIVE)
                && !status.equals(User.STATUS_INACTIVE)) {
            throw new IllegalArgumentException("Unknown status.");
        }
        User target = findById(userId);
        if (target == null) {
            return false;
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // A student needs a student number and a course. Give them when the account has none yet.
                if (role.equals(User.ROLE_STUDENT)) {
                    if (target.studentNumber() == null || target.studentNumber().isBlank()) {
                        saveStudentField(conn, userId, "student_number", nextStudentNumber(conn));
                    }
                    if (target.course() == null || target.course().isBlank()) {
                        saveStudentField(conn, userId, "course", "N/A");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET role = ?, account_status = ? WHERE user_id = ?")) {
                    ps.setString(1, role);
                    ps.setString(2, status);
                    ps.setInt(3, userId);
                    ps.executeUpdate();
                }
                if (actorId != null) {
                    writeRoleStatusLogs(conn, actorId, target, role, status);
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // The column name is always written in this file (student_number or course).
    private void saveStudentField(Connection conn, int userId, String column, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET " + column + " = ? WHERE user_id = ?")) {
            ps.setString(1, value);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // Logs what changed: USER_UPDATED for a role / status change, plus USER_APPROVED or USER_REJECTED
    // when a PENDING account is approved or turned down. Nothing is logged when nothing changed.
    private void writeRoleStatusLogs(Connection conn, int actorId, User target, String role, String status)
            throws SQLException {
        List<String> changes = new ArrayList<>();
        if (!role.equals(target.role())) {
            changes.add("role of User " + target.userId() + " to " + role);
        }
        if (!status.equals(target.accountStatus())) {
            changes.add("status of User " + target.userId() + " to " + status);
        }
        if (changes.isEmpty()) {
            return;
        }
        logDao.logAction(conn, actorId, "USER_UPDATED",
                "Admin [" + actorId + "] changed " + String.join(" and ", changes), null, null);
        if (target.isPending() && status.equals(User.STATUS_ACTIVE)) {
            logDao.logAction(conn, actorId, "USER_APPROVED",
                    "Admin [" + actorId + "] approved User [" + target.userId() + "]", null, null);
        } else if (target.isPending() && status.equals(User.STATUS_INACTIVE)) {
            logDao.logAction(conn, actorId, "USER_REJECTED",
                    "Admin [" + actorId + "] rejected User [" + target.userId() + "]", null, null);
        }
    }

    // Admin: how many accounts are waiting for approval.
    public int countPendingAccounts(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE account_status = 'PENDING'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countActiveAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND account_status = 'ACTIVE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}