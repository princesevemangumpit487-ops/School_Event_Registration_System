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
        TablePrinter table = new TablePrinter("ID", "Event", "Date", "Location", "Capacity",
                "Approved", "Available", "Status");
        for (Event e : events) {
            table.addRow(e.eventId(), e.eventName(), e.eventDate(), e.location(), e.capacity(),
                    e.approvedCount(), e.availableSlots(), e.availabilityText());
        }
        table.print();
        System.out.println("Events shown: " + events.size());
    }

    // One event shown as a one-row table (used when editing or removing an event).
    public static void printEvent(Event e) {
        printList(java.util.List.of(e));
    }

    public static void printFull(Event e) {
        InputHelper.header("EVENT FULL");
        System.out.println("This event has reached its maximum capacity.");
        System.out.println();
        TablePrinter.printFields(
                "Capacity", e.capacity(),
                "Approved Participants", e.approvedCount(),
                "Available Slots", e.availableSlots());
        System.out.println();
    }
}