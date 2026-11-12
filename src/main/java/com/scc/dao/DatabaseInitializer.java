package com.scc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes sure all database tables exist before the menus start.
 * Main calls initialize() once at startup; it is safe to run on every launch
 * because every statement uses CREATE ... IF NOT EXISTS.
 * It also seeds a default Admin account (username "admin") the first time the system runs,
 * and hashes any old plain-text passwords. Every later account is approved by an admin.
 * Change the password after the first login (Manage Users -> Reset Password).
 */
public class DatabaseInitializer {
    // Default admin created automatically when no "admin" account exists yet.
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_NAME = "System Administrator";
    private static final String DEFAULT_ADMIN_CONTACT = "N/A";

    public static void initialize() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "full_name TEXT NOT NULL, "
                    + "contact_number TEXT NOT NULL, "
                    + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "password_hash TEXT NOT NULL, "
                    + "role TEXT NOT NULL CHECK (role IN ('STUDENT', 'STAFF', 'ADMIN')), "
                    + "account_status TEXT NOT NULL DEFAULT 'PENDING' "
                    + "CHECK (account_status IN ('PENDING', 'ACTIVE', 'INACTIVE')), "
                    + "created_at TEXT NOT NULL, "
                    + "student_number TEXT UNIQUE, "
                    + "course TEXT)");

            st.execute("CREATE TABLE IF NOT EXISTS events ("
                    + "event_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "event_name TEXT NOT NULL, "
                    + "event_date TEXT NOT NULL, "
                    + "location TEXT NOT NULL, "
                    + "capacity INTEGER NOT NULL CHECK (capacity > 0), "
                    + "event_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (event_status IN ('ACTIVE', 'REMOVED')))");

            st.execute("CREATE TABLE IF NOT EXISTS registrations ("
                    + "registration_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "student_id INTEGER NOT NULL REFERENCES users(user_id), "
                    + "event_id INTEGER NOT NULL REFERENCES events(event_id), "
                    + "registration_date TEXT NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'Pending' "
                    + "CHECK (status IN ('Pending', 'Approved', 'Rejected', 'Cancelled')))");

            // A student cannot have two Pending/Approved registrations for the same event.
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_active_registration "
                    + "ON registrations (student_id, event_id) WHERE status IN ('Pending', 'Approved')");

            st.execute("CREATE TABLE IF NOT EXISTS activity_logs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE, "
                    + "event_id INTEGER REFERENCES events(event_id), "
                    + "registration_id INTEGER REFERENCES registrations(registration_id) ON DELETE SET NULL, "
                    + "action_type TEXT NOT NULL, "
                    + "description TEXT NOT NULL, "
                    + "timestamp TEXT NOT NULL)");

            hashPlainTextPasswords(conn);
            seedDefaultAdmin(conn);
        }
    }

    // Creates the default admin only if that username does not exist yet (never overwrites an account).
    private static void seedDefaultAdmin(Connection conn) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            check.setString(1, DEFAULT_ADMIN_USERNAME);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }
        String sql = "INSERT INTO users (full_name, contact_number, username, password_hash, role, "
                + "account_status, created_at) VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_ADMIN_NAME);
            ps.setString(2, DEFAULT_ADMIN_CONTACT);
            ps.setString(3, DEFAULT_ADMIN_USERNAME);
            ps.setString(4, PasswordHasher.hash(DEFAULT_ADMIN_PASSWORD)); // saved hashed, never as plain text
            ps.setString(5, DatabaseConnection.now());
            ps.executeUpdate();
        }
    }

    // Old accounts may still have a plain-text password (for example "123"). Hash them once;
    // the password stays the same, so those users can still log in with it.
    private static void hashPlainTextPasswords(Connection conn) throws SQLException {
        List<Object[]> toUpdate = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id, password_hash FROM users")) {
            while (rs.next()) {
                String stored = rs.getString(2);
                if (!PasswordHasher.isHashed(stored)) {
                    toUpdate.add(new Object[]{rs.getInt(1), stored});
                }
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
            for (Object[] row : toUpdate) {
                ps.setString(1, PasswordHasher.hash((String) row[1]));
                ps.setInt(2, (Integer) row[0]);
                ps.executeUpdate();
            }
        }
    }
}