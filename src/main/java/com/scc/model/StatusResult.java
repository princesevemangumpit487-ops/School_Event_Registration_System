package com.scc.model;

/** The result of approving, rejecting, or changing the status of a registration. */
public enum StatusResult {
    CHANGED,          // the change was saved
    NOT_FOUND,        // there is no registration with that ID
    INVALID_CHANGE,   // the status rules do not allow this change
    EVENT_FULL,       // the event has no available slots left
    EVENT_REMOVED,    // the event was removed by the admin
    DUPLICATE,        // the student already has an active registration for this event
    ALREADY_CHANGED   // someone else changed the registration first
}