package com.scc.model;

/** One row of the Registration Activity Log. eventId / registrationId are null when they do not apply. */
public record ActivityLog(int logId, String username, String userRole, String action,
                          Integer eventId, Integer registrationId, String timestamp) {
}