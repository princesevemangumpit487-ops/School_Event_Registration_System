package com.scc.ui;

import com.scc.dao.EventDao;
import com.scc.dao.RegistrationDao;
import com.scc.model.Event;
import com.scc.model.Registration;
import com.scc.model.StatusResult;
import com.scc.model.User;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** The Event Staff screens for registrations: pending list, approve, reject, update, and participant list. */
public class RegistrationMenu {
    private final InputHelper input;
    private final RegistrationDao registrationDao = new RegistrationDao();
    private final EventDao eventDao = new EventDao();

    public RegistrationMenu(InputHelper input) {
        this.input = input;
    }

    // The pending list followed by the Approve / Reject / View Details actions.
    public void pendingRegistrations(User staff) throws SQLException {
        boolean back = false;
        while (!back) {
            InputHelper.header("PENDING REGISTRATIONS");
            List<Registration> pending = registrationDao.findPending(staff);
            RegistrationView.printForStaff(pending);
            if (pending.isEmpty()) {
                return;
            }
            System.out.println();
            System.out.println("Actions:");
            System.out.println("1. Approve");
            System.out.println("2. Reject");
            System.out.println("3. View Details");
            System.out.println("4. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
            switch (choice) {
                case 1:
                    approve(staff);
                    break;
                case 2:
                    reject(staff);
                    break;
                case 3:
                    viewDetails(staff);
                    break;
                default:
                    back = true;
                    break;
            }
        }
    }

    private void viewDetails(User staff) throws SQLException {
        int id = input.readInt("Enter Registration ID: ");
        Registration reg = registrationDao.findById(staff, id);
        if (reg == null) {
            System.out.println("Registration not found.");
            return;
        }
        RegistrationView.printDetails(reg);
        Event event = eventDao.findActiveById(reg.eventId());
        if (event != null) {
            System.out.println("Capacity: " + event.capacity());
            System.out.println("Available Slots: " + event.availableSlots());
        }
    }

    public void approve(User staff) throws SQLException {
        InputHelper.header("APPROVE REGISTRATION");
        int id = input.readInt("Enter Registration ID: ");
        StatusResult result = registrationDao.approve(staff, id);
        if (result == StatusResult.CHANGED) {
            System.out.println("Registration " + id + " approved.");
        } else {
            showProblem(result, id);
        }
    }

    public void reject(User staff) throws SQLException {
        InputHelper.header("REJECT REGISTRATION");
        int id = input.readInt("Enter Registration ID: ");
        Registration reg = registrationDao.findById(staff, id);
        if (reg == null) {
            System.out.println("Registration not found.");
            return;
        }
        if (!reg.status().equals(Registration.STATUS_PENDING)) {
            System.out.println("Only Pending registrations can be rejected. Current status: " + reg.status());
            return;
        }
        System.out.println("Student: " + reg.studentName() + " | Event: " + reg.eventName());
        if (!input.readConfirm("Reject this registration? (Y/N): ")) {
            System.out.println("Registration was not rejected.");
            return;
        }
        StatusResult result = registrationDao.reject(staff, id);
        if (result == StatusResult.CHANGED) {
            System.out.println("Registration " + id + " rejected.");
        } else {
            showProblem(result, id);
        }
    }

    // Staff changes the registration date or the status.
    public void update(User staff) throws SQLException {
        InputHelper.header("UPDATE REGISTRATION");
        int id = input.readInt("Enter Registration ID: ");
        if (registrationDao.findById(staff, id) == null) {
            System.out.println("Registration not found.");
            return;
        }
        boolean done = false;
        while (!done) {
            Registration reg = registrationDao.findById(staff, id); // reload to show the saved values
            System.out.println();
            System.out.println("Registration ID: " + reg.registrationId());
            System.out.println("Student: " + reg.studentName());
            System.out.println("Event: " + reg.eventName());
            System.out.println("Registered On: " + reg.registrationDate());
            System.out.println("Status: " + reg.status());
            System.out.println();
            System.out.println("1. Change Registration Date");
            System.out.println("2. Change Status");
            System.out.println("3. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
            if (choice == 1) {
                changeDate(staff, id);
            } else if (choice == 2) {
                changeStatus(staff, id);
            } else {
                done = true;
            }
        }
    }

    private void changeDate(User staff, int id) throws SQLException {
        String text = input.readNonEmpty("New Registration Date (yyyy-MM-dd): ");
        try {
            String date = LocalDate.parse(text).toString(); // checks that the date is valid
            if (registrationDao.updateDate(staff, id, date)) {
                System.out.println("Registration date updated.");
            }
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date. Please use the format yyyy-MM-dd (example: 2026-10-07).");
        }
    }

    private void changeStatus(User staff, int id) throws SQLException {
        System.out.println("Change status to:");
        System.out.println("1. Approved");
        System.out.println("2. Rejected");
        System.out.println("3. Pending");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
        if (choice == 4) {
            return;
        }
        String newStatus = choice == 1 ? Registration.STATUS_APPROVED
                : (choice == 2 ? Registration.STATUS_REJECTED : Registration.STATUS_PENDING);
        StatusResult result = registrationDao.changeStatus(staff, id, newStatus);
        if (result == StatusResult.CHANGED) {
            System.out.println("Status changed to " + newStatus + ".");
        } else {
            showProblem(result, id);
        }
    }

    // Explains in simple words why a change was not saved.
    private void showProblem(StatusResult result, int id) throws SQLException {
        switch (result) {
            case NOT_FOUND:
                System.out.println("Registration not found.");
                break;
            case INVALID_CHANGE:
                System.out.println("This status change is not allowed for this registration.");
                break;
            case EVENT_FULL:
                System.out.println("Cannot approve registration.");
                System.out.println();
                System.out.println("The event has reached its maximum capacity.");
                break;
            case EVENT_REMOVED:
                System.out.println("This event was removed, so the registration cannot be approved or reopened.");
                break;
            case DUPLICATE:
                System.out.println("The student already has an active registration for this event.");
                break;
            default:
                System.out.println("Nothing was changed. The registration was updated by someone else.");
                break;
        }
    }

    // Participant list: shows ONLY Approved registrations of one event.
    public void participantList(User staff) throws SQLException {
        InputHelper.header("VIEW PARTICIPANT LIST");
        int eventId = input.readInt("Enter Event ID: ");
        Event event = eventDao.findActiveById(eventId);
        if (event == null) {
            System.out.println("Event not found.");
            return;
        }
        InputHelper.header(event.eventName().toUpperCase() + " PARTICIPANTS");
        System.out.println("Event: " + event.eventName());
        System.out.println("Location: " + event.location());
        System.out.println("Capacity: " + event.capacity());
        System.out.println("Approved Participants: " + event.approvedCount());
        System.out.println("Available Slots: " + event.availableSlots());
        InputHelper.printDivider();

        List<Registration> participants = registrationDao.findApprovedByEvent(staff, eventId);
        if (participants.isEmpty()) {
            System.out.println("No approved participants yet.");
        }
        int number = 1;
        for (Registration r : participants) {
            System.out.println(number + ". " + r.studentName());
            System.out.println("   Student No: " + r.studentNumber() + " | Course: " + (r.course() == null ? "N/A" : r.course()));
            number++;
        }
        InputHelper.printLine();
    }
}