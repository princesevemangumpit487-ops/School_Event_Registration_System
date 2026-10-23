package com.scc.dao;

import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Makes sure all database tables exist before the menus start.
 * Main calls initialize() once at startup; it is safe to run on every launch
 * because every statement uses CREATE ... IF NOT EXISTS.
 */
public class DatabaseInitializer {
    // Default accounts that always exist (created only if the username is not in the database yet).
    // Students can only self-register; more admins and staff are added by an admin.
    private static final String DEFAULT_PASSWORD = "1234";

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
                    + "log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL REFERENCES users(user_id), "
                    + "action TEXT NOT NULL, "
                    + "event_id INTEGER REFERENCES events(event_id), "
                    + "registration_id INTEGER REFERENCES registrations(registration_id) ON DELETE SET NULL, "
                    + "timestamp TEXT NOT NULL)");
        }
        seedDefaultAccounts();
    }

    // Creates the ready-made Admin and Event Staff accounts (ACTIVE) if they do not exist yet.
    private static void seedDefaultAccounts() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            seedAccount(conn, "System Administrator", "09000000001", "admin", DEFAULT_PASSWORD, User.ROLE_ADMIN);
            seedAccount(conn, "Event Staff", "09000000002", "staff", DEFAULT_PASSWORD, User.ROLE_STAFF);
        }
    }

    private static void seedAccount(Connection conn, String fullName, String contact, String username,
                                    String password, String role) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return; // already exists, do nothing
                }
            }
        }
        String sql = "INSERT INTO users (full_name, contact_number, username, password_hash, role, "
                + "account_status, created_at, student_number, course) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, contact);
            ps.setString(3, username);
            ps.setString(4, PasswordHasher.hash(password));
            ps.setString(5, role);
            ps.setString(6, User.STATUS_ACTIVE);
            ps.setString(7, DatabaseConnection.now());
            ps.executeUpdate();
        }
    }
}
