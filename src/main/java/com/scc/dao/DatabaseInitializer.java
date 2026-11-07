package com.scc.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Makes sure all database tables exist before the menus start.
 * Main calls initialize() once at startup; it is safe to run on every launch
 * because every statement uses CREATE ... IF NOT EXISTS.
 * The system starts with NO accounts: the first Admin registers from the Register screen
 * (see UserDao.register), and every later account is approved by an admin.
 */
public class DatabaseInitializer {

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
        }
    }
}
