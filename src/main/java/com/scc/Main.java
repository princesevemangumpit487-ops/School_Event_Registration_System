package com.scc;

import com.scc.dao.DatabaseInitializer;
import com.scc.dao.UserDao;
import com.scc.ui.Authentication;
import com.scc.ui.InputHelper;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * School Event Registration System - console application (entry point).
 * Main only starts the program: Initialize Database -> create the shared DAO and input
 * -> hand control to Authentication.start(). Login, registration, and role routing live there.
 */
public class Main {
    public static void main(String[] args) {
        // 1. Create the tables if they do not exist yet.
        try {
            DatabaseInitializer.initialize();
        } catch (SQLException e) {
            System.out.println("The database could not be prepared.");
            System.out.println("Make sure the sqlite-jdbc dependency is in pom.xml and Maven has finished loading.");
            System.out.println("Detail: " + e.getMessage());
            return;
        }

        // 2. Create the shared objects once (one Scanner, one UserDao for the whole program).
        Scanner scanner = new Scanner(System.in);
        InputHelper input = new InputHelper(scanner);
        UserDao userDao = new UserDao();

        // 3. Hand over to the login / register screen.
        new Authentication(input, userDao).start();
        scanner.close();
    }
}