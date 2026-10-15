package com.scc.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Opens SQLite connections and creates the tables (the same tables are in the SQL script).
 * Every DAO uses this one class, so the whole system uses ONE database file.
 * No connection is kept open: every DAO method opens its own and closes it.
 */
public class DatabaseConnection {
    // The database file is created in the project folder the first time the program runs.
    private static final String DB_URL = "jdbc:sqlite:event_registration.db";
    private static final String DB_FILE = "event_registration.db";

    public static String getDatabasePath() {
        return DB_FILE;
    }

    public static long getDatabaseFileSize() {
        File file = new File(DB_FILE);
        return file.exists() ? file.length() : 0;
    }

    // Opens a new connection and turns on foreign-key checking (SQLite has it OFF by default).
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    // Current date and time as text, for example 2026-10-11 14:30:05
    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // Returns the ID of the row that was just inserted on this same connection.
    public static int lastInsertId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Creates the tables if they do not exist yet.
    public static void initializeDatabase() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
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
    }
}