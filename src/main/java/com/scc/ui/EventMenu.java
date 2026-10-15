package com.scc.ui;

import com.scc.dao.EventDao;
import com.scc.model.Event;
import com.scc.model.User;
import java.sql.SQLException;

/** Admin screens for events: create, view, search, update, and remove. */
public class EventMenu {
    private final InputHelper input;
    private final EventDao eventDao = new EventDao();
    private final EventBrowser browser;

    public EventMenu(InputHelper input) {
        this.input = input;
        this.browser = new EventBrowser(input, eventDao);
    }

    public void run(User admin) {
        boolean back = false;
        while (!back) {
            InputHelper.header("MANAGE EVENTS");
            System.out.println("1. Create Event");
            System.out.println("2. View All Events");
            System.out.println("3. Search Events");
            System.out.println("4. Update Event");
            System.out.println("5. Remove Event");
            System.out.println("6. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 6);
            try {
                switch (choice) {
                    case 1:
                        createEvent(admin);
                        break;
                    case 2:
                        browser.viewEvents(admin);
                        break;
                    case 3:
                        browser.search(admin);
                        break;
                    case 4:
                        updateEvent(admin);
                        break;
                    case 5:
                        removeEvent(admin);
                        break;
                    default:
                        back = true;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    private void createEvent(User admin) throws SQLException {
        InputHelper.header("CREATE SCHOOL EVENT");
        String name = input.readNonEmpty("Event Name: ");
        String date = input.readNonEmpty("Event Date (example: October 15, 2026): ");
        String location = input.readNonEmpty("Location: ");
        int capacity = input.readInt("Capacity: ");
        while (capacity <= 0) {
            System.out.println("Capacity must be greater than 0.");
            capacity = input.readInt("Capacity: ");
        }
        int eventId = eventDao.create(admin, name, date, location, capacity);
        System.out.println();
        System.out.println("Event created successfully!");
        System.out.println("Event ID: " + eventId);
    }

    private void updateEvent(User admin) throws SQLException {
        InputHelper.header("UPDATE EVENT");
        int eventId = input.readInt("Enter Event ID: ");
        if (eventDao.findActiveById(eventId) == null) {
            System.out.println("Event not found.");
            return;
        }
        boolean done = false;
        while (!done) {
            Event event = eventDao.findActiveById(eventId); // reload to show the saved values
            System.out.println();
            EventView.printEvent(event);
            System.out.println("1. Change Event Name");
            System.out.println("2. Change Date");
            System.out.println("3. Change Location");
            System.out.println("4. Change Capacity");
            System.out.println("5. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);
            switch (choice) {
                case 1:
                    eventDao.updateName(admin, event, input.readNonEmpty("New Event Name: "));
                    System.out.println("Event name updated.");
                    break;
                case 2:
                    eventDao.updateDate(admin, event, input.readNonEmpty("New Event Date: "));
                    System.out.println("Event date updated.");
                    break;
                case 3:
                    eventDao.updateLocation(admin, event, input.readNonEmpty("New Location: "));
                    System.out.println("Event location updated.");
                    break;
                case 4:
                    changeCapacity(admin, event);
                    break;
                default:
                    done = true;
                    break;
            }
        }
    }

    // The capacity cannot go below the number of approved participants.
    private void changeCapacity(User admin, Event event) throws SQLException {
        int newCapacity = input.readInt("New Capacity: ");
        if (newCapacity <= 0) {
            System.out.println("Capacity must be greater than 0.");
        } else if (eventDao.updateCapacity(admin, event, newCapacity)) {
            System.out.println("Event capacity updated.");
        } else {
            System.out.println("Cannot reduce capacity below the current number of approved participants.");
            System.out.println();
            System.out.println("Current Approved: " + event.approvedCount());
            System.out.println("Requested Capacity: " + newCapacity);
        }
    }

    private void removeEvent(User admin) throws SQLException {
        InputHelper.header("REMOVE EVENT");
        int eventId = input.readInt("Enter Event ID: ");
        Event event = eventDao.findActiveById(eventId);
        if (event == null) {
            System.out.println("Event not found.");
            return;
        }
        EventView.printEvent(event);
        if (!input.readConfirm("Are you sure you want to remove this event? (Y/N): ")) {
            System.out.println("Event was not removed.");
            return;
        }
        if (eventDao.remove(admin, event)) {
            System.out.println("Event removed successfully. Its registration records are kept.");
        } else {
            System.out.println("The event could not be removed.");
        }
    }
}