package com.scc.model;

/** One line of the Event Registration Report: the registration counts of one event. */
public record EventReportRow(int eventId, String eventName, String eventDate, String location, int capacity,
                             int approved, int pending, int rejected, int cancelled) {

    public int total() {
        return approved + pending + rejected + cancelled;
    }

    public int remainingSlots() {
        return capacity - approved;
    }
}