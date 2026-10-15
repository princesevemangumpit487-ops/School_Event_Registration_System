package com.scc.ui;

import com.scc.dao.EventDao;
import com.scc.model.User;
import java.sql.SQLException;

/** View and search events. Used by the Student, Staff, and Admin menus. */
public class EventBrowser {
    private final InputHelper input;
    private final EventDao eventDao;

    public EventBrowser(InputHelper input, EventDao eventDao) {
        this.input = input;
        this.eventDao = eventDao;
    }

    // Students see "AVAILABLE EVENTS"; staff and admin see "ALL SCHOOL EVENTS".
    public void viewEvents(User actor) throws SQLException {
        InputHelper.header(actor.isStudent() ? "AVAILABLE EVENTS" : "ALL SCHOOL EVENTS");
        EventView.printList(eventDao.findActiveEvents());
    }

    public void search(User actor) throws SQLException {
        InputHelper.header("SEARCH EVENTS");
        System.out.println("1. Search by Event Name");
        System.out.println("2. Search by Date");
        System.out.println("3. View All Events");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
        switch (choice) {
            case 1:
                String name = input.readNonEmpty("Enter event name: ");
                System.out.println();
                EventView.printList(eventDao.searchByName(name));
                break;
            case 2:
                String date = input.readNonEmpty("Enter date (example: October 15): ");
                System.out.println();
                EventView.printList(eventDao.searchByDate(date));
                break;
            case 3:
                viewEvents(actor);
                break;
            default:
                break;
        }
    }
}