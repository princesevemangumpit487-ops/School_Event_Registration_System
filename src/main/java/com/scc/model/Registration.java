package com.scc.model;

/**
 * A student's registration for an event. A new registration always starts as Pending.
 * The student and event details come from the database (joined tables), so they are always up to date.
 */
public record Registration(int registrationId, int studentId, String studentName, String studentNumber,
                           String course, int eventId, String eventName, String eventDate, String location,
                           String registrationDate, String status) {

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_APPROVED = "Approved";
    public static final String STATUS_REJECTED = "Rejected";
    public static final String STATUS_CANCELLED = "Cancelled";

    // Allowed staff changes: Pending -> Approved/Rejected, Approved -> Rejected, Rejected -> Pending.
    // Cancelled is final (only the student can cancel, and only while Pending).
    public static boolean isValidChange(String from, String to) {
        switch (from) {
            case STATUS_PENDING:
                return to.equals(STATUS_APPROVED) || to.equals(STATUS_REJECTED);
            case STATUS_APPROVED:
                return to.equals(STATUS_REJECTED);
            case STATUS_REJECTED:
                return to.equals(STATUS_PENDING);
            default:
                return false;
        }
    }
}