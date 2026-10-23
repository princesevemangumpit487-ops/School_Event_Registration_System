package com.scc.ui;

import com.scc.dao.EventDao;
import com.scc.dao.PasswordHasher;
import com.scc.dao.RegistrationDao;
import com.scc.dao.UserDao;
import com.scc.model.Event;
import com.scc.model.RegisterResult;
import com.scc.model.Registration;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The Student dashboard: students only see and change their own registrations and profile. */
public class StudentMenu {
    private final InputHelper input;
    private final EventDao eventDao = new EventDao();
    private final RegistrationDao registrationDao = new RegistrationDao();
    private final UserDao userDao;
    private final EventBrowser browser;
    private final AccountForm form;

    public StudentMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.browser = new EventBrowser(input, eventDao);
        this.form = new AccountForm(input, userDao);
    }

    public void run(User student) {
        User user = student;
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("STUDENT DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. View Available Events");
            System.out.println("2. Search Events");
            System.out.println("3. Register for Event");
            System.out.println("4. My Registrations");
            System.out.println("5. Registration History");
            System.out.println("6. Edit My Profile");
            System.out.println("7. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);

            try {
                switch (choice) {
                    case 1:
                        browser.viewEvents(user);
                        break;
                    case 2:
                        browser.search(user);
                        break;
                    case 3:
                        registerForEvent(user);
                        break;
                    case 4:
                        myRegistrations(user);
                        break;
                    case 5:
                        InputHelper.header("REGISTRATION HISTORY");
                        RegistrationView.printForStudent(registrationDao.findStudentRegistrations(user, true), true);
                        break;
                    case 6:
                        user = editProfile(user);
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    // Student picks an event, sees its details, and confirms. The new registration is Pending.
    private void registerForEvent(User user) throws SQLException {
        InputHelper.header("EVENT REGISTRATION");
        int eventId = input.readInt("Enter Event ID: ");
        Event event = eventDao.findActiveById(eventId);
        if (event == null) {
            System.out.println("Event not found.");
            return;
        }
        if (registrationDao.hasActiveRegistration(user.userId(), eventId)) { // duplicate prevention
            System.out.println("You are already registered for this event.");
            return;
        }
        if (event.isFull()) {
            EventView.printFull(event);
            System.out.println("You cannot register for this event.");
            return;
        }

        System.out.println();
        System.out.println("Event: " + event.eventName());
        System.out.println("Date: " + event.eventDate());
        System.out.println("Location: " + event.location());
        System.out.println("Available Slots: " + event.availableSlots());
        System.out.println();
        if (!input.readConfirm("Register for this event? (Y/N): ")) {
            System.out.println("Registration cancelled.");
            return;
        }

        // The DAO checks everything again, so the rules hold even if something changed in the meantime.
        RegisterResult result = registrationDao.register(user, eventId);
        switch (result.outcome()) {
            case REGISTERED:
                System.out.println();
                System.out.println("Registration successful!");
                System.out.println();
                System.out.println("Registration ID: " + result.registrationId());
                System.out.println("Status: " + Registration.STATUS_PENDING);
                break;
            case ALREADY_REGISTERED:
                System.out.println("You are already registered for this event.");
                break;
            case EVENT_FULL:
                System.out.println("Sorry, the event just became full. Registration was not saved.");
                break;
            default:
                System.out.println("Event not found.");
                break;
        }
    }

    // Lists the student's current registrations and offers to cancel a Pending one.
    private void myRegistrations(User user) throws SQLException {
        InputHelper.header("MY REGISTRATIONS");
        List<Registration> list = registrationDao.findStudentRegistrations(user, false);
        RegistrationView.printForStudent(list, false);
        if (list.isEmpty() || !input.readConfirm("Do you want to cancel a registration? (Y/N): ")) {
            return;
        }
        int id = input.readInt("Enter Registration ID to cancel: ");
        Registration reg = registrationDao.findStudentRegistration(user, id); // only the student's own records
        if (reg == null) {
            System.out.println("Registration not found.");
            return;
        }
        if (!reg.status().equals(Registration.STATUS_PENDING)) {
            System.out.println("This registration can no longer be cancelled.");
            return;
        }
        System.out.println("Event: " + reg.eventName());
        System.out.println("Registration Status: " + reg.status());
        if (!input.readConfirm("Cancel? (Y/N): ")) {
            System.out.println("Registration was not cancelled.");
            return;
        }
        if (registrationDao.cancelOwn(user, id)) {
            System.out.println("Registration cancelled. The record is kept in your history.");
        } else {
            System.out.println("The registration could not be cancelled. Its status may have changed.");
        }
    }

    // A student can change name, contact number, course, and password (not role, status, or student number).
    private User editProfile(User start) throws SQLException {
        User user = start;
        boolean back = false;
        while (!back) {
            InputHelper.header("MY PROFILE");
            System.out.println("Student Number: " + user.studentNumber());
            System.out.println("Username: " + user.username());
            System.out.println("Name: " + user.fullName());
            System.out.println("Contact: " + user.contactNumber());
            System.out.println("Course: " + user.course());
            System.out.println();
            System.out.println("1. Change Name");
            System.out.println("2. Change Contact Number");
            System.out.println("3. Change Course");
            System.out.println("4. Change Password");
            System.out.println("5. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);
            switch (choice) {
                case 1:
                    String name = input.readNonEmpty("New Name: ");
                    userDao.updateOwnField(user, "full_name", name);
                    user = user.withFullName(name);
                    System.out.println("Saved: name updated.");
                    break;
                case 2:
                    String contact = form.readContact("New Contact Number: ");
                    userDao.updateOwnField(user, "contact_number", contact);
                    user = user.withContactNumber(contact);
                    System.out.println("Saved: contact number updated.");
                    break;
                case 3:
                    String course = input.readNonEmpty("New Course: ");
                    userDao.updateOwnField(user, "course", course);
                    user = user.withCourse(course);
                    System.out.println("Saved: course updated.");
                    break;
                case 4:
                    String current = input.readNonEmpty("Current Password: ");
                    if (!PasswordHasher.verify(current, user.passwordHash())) {
                        System.out.println("Wrong current password. Password not changed.");
                    } else {
                        String newHash = PasswordHasher.hash(form.readNewPassword());
                        userDao.updateOwnField(user, "password_hash", newHash);
                        user = user.withPasswordHash(newHash);
                        System.out.println("Saved: password updated.");
                    }
                    break;
                default:
                    back = true;
                    break;
            }
        }
        return user;
    }
}