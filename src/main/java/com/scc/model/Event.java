package com.scc.model;

/**
 * A school event. approvedCount is calculated by the database (how many Approved registrations).
 * eventStatus is ACTIVE, or REMOVED when the admin removed the event (the record is kept).
 */
public record Event(int eventId, String eventName, String eventDate, String location, int capacity,
                    String eventStatus, int approvedCount) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REMOVED = "REMOVED";

    public boolean isActive() {
        return eventStatus.equals(STATUS_ACTIVE);
    }

    // availableSlots = capacity - approvedCount
    public int availableSlots() {
        return capacity - approvedCount;
    }

    public boolean isFull() {
        return availableSlots() <= 0;
    }

    public String availabilityText() {
        return availableSlots() > 0 ? "AVAILABLE" : "FULL";
    }
}