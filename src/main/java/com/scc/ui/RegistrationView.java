package com.scc.ui;

import com.scc.model.Registration;
import java.util.List;

/** Prints registrations for students, for staff/admin, and as a detail card. */
public class RegistrationView {

    public static void printForStudent(List<Registration> list, boolean showDate) {
        if (list.isEmpty()) {
            System.out.println("No registrations found.");
            return;
        }
        for (Registration r : list) {
            System.out.println("Registration ID: " + r.registrationId());
            System.out.println("Event: " + r.eventName());
            System.out.println("Date: " + r.eventDate());
            System.out.println("Location: " + r.location());
            if (showDate) {
                System.out.println("Registered On: " + r.registrationDate());
            }
            System.out.println("Registration Status: " + r.status());
            InputHelper.printDivider();
        }
        System.out.println("Registrations shown: " + list.size());
    }

    public static void printForStaff(List<Registration> list) {
        if (list.isEmpty()) {
            System.out.println("No registrations found.");
            return;
        }
        for (Registration r : list) {
            System.out.println("Registration ID: " + r.registrationId());
            System.out.println("Student: " + r.studentName());
            System.out.println("Student Number: " + r.studentNumber());
            System.out.println("Event: " + r.eventName());
            System.out.println("Status: " + r.status());
            InputHelper.printDivider();
        }
        System.out.println("Registrations shown: " + list.size());
    }

    public static void printDetails(Registration r) {
        InputHelper.header("REGISTRATION DETAILS");
        System.out.println("Registration ID: " + r.registrationId());
        System.out.println("Student: " + r.studentName());
        System.out.println("Student Number: " + r.studentNumber());
        System.out.println("Course: " + (r.course() == null ? "N/A" : r.course()));
        System.out.println("Event: " + r.eventName());
        System.out.println("Event Date: " + r.eventDate());
        System.out.println("Location: " + r.location());
        System.out.println("Registered On: " + r.registrationDate());
        System.out.println("Status: " + r.status());
    }
}