package com.scc;

import com.scc.dao.DatabaseConnection;
import com.scc.ui.InputHelper;
import com.scc.ui.MainMenu;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * School Event Registration System - console application (entry point).
 * Main prepares the database and starts the first menu. The screens are in the ui package,
 * the SQL is in the dao package, and the data classes are in the model package.
 */
public class Main {
    public static void main(String[] args) {
        // Create the tables if they do not exist yet.
        try {
            DatabaseConnection.initializeDatabase();
        } catch (SQLException e) {
            System.out.println("The database could not be prepared.");
            System.out.println("Make sure the sqlite-jdbc dependency is in pom.xml and Maven has finished loading.");
            System.out.println("Detail: " + e.getMessage());
            return;
        }

        Scanner scanner = new Scanner(System.in);
        new MainMenu(new InputHelper(scanner)).run();
        scanner.close();
    }
}