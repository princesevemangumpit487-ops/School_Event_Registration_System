package com.scc.ui;

import com.scc.model.Event;
import java.util.List;

/** Prints events and the "EVENT FULL" message. */
public class EventView {

    public static void printList(List<Event> events) {
        if (events.isEmpty()) {
            System.out.println("No events found.");
            return;
        }
        for (Event event : events) {
            printEvent(event);
        }
        System.out.println("Events shown: " + events.size());
    }

    public static void printEvent(Event e) {
        System.out.println("Event ID: " + e.eventId());
        System.out.println("Event: " + e.eventName());
        System.out.println("Date: " + e.eventDate());
        System.out.println("Location: " + e.location());
        System.out.println("Capacity: " + e.capacity());
        System.out.println("Approved Participants: " + e.approvedCount());
        System.out.println("Available Slots: " + e.availableSlots());
        System.out.println("Status: " + e.availabilityText());
        InputHelper.printDivider();
    }

    public static void printFull(Event e) {
        InputHelper.header("EVENT FULL");
        System.out.println("This event has reached its maximum capacity.");
        System.out.println();
        System.out.println("Capacity: " + e.capacity());
        System.out.println("Approved Participants: " + e.approvedCount());
        System.out.println("Available Slots: " + e.availableSlots());
        System.out.println();
    }
}