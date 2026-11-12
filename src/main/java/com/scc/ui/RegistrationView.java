package com.scc.ui;

import com.scc.model.Registration;
import java.util.List;

/** Prints registrations for students, for staff/admin, and as a detail table. */
public class RegistrationView {

    public static void printForStudent(List<Registration> list, boolean showDate) {
        if (list.isEmpty()) {
            System.out.println("No registrations found.");
            return;
        }
        TablePrinter table = showDate
                ? new TablePrinter("Reg ID", "Event", "Date", "Location", "Registered On", "Status")
                : new TablePrinter("Reg ID", "Event", "Date", "Location", "Status");
        for (Registration r : list) {
            if (showDate) {
                table.addRow(r.registrationId(), r.eventName(), r.eventDate(), r.location(),
                        r.registrationDate(), r.status());
            } else {
                table.addRow(r.registrationId(), r.eventName(), r.eventDate(), r.location(), r.status());
            }
        }
        table.print();
        System.out.println("Registrations shown: " + list.size());
    }

    public static void printForStaff(List<Registration> list) {
        if (list.isEmpty()) {
            System.out.println("No registrations found.");
            return;
        }
        TablePrinter table = new TablePrinter("Reg ID", "Student", "Student No.", "Event", "Status");
        for (Registration r : list) {
            table.addRow(r.registrationId(), r.studentName(), r.studentNumber(), r.eventName(), r.status());
        }
        table.print();
        System.out.println("Registrations shown: " + list.size());
    }

    public static void printDetails(Registration r) {
        InputHelper.header("REGISTRATION DETAILS");
        TablePrinter.printFields(
                "Registration ID", r.registrationId(),
                "Student", r.studentName(),
                "Student Number", r.studentNumber(),
                "Course", r.course() == null ? "N/A" : r.course(),
                "Event", r.eventName(),
                "Event Date", r.eventDate(),
                "Location", r.location(),
                "Registered On", r.registrationDate(),
                "Status", r.status());
    }
}