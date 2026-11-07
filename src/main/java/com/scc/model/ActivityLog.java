package com.scc.model;

/**
 * One row of the Activity Log (table activity_logs). eventId / registrationId are null when they do not apply.
 * actionType is a short code such as USER_APPROVED, USER_UPDATED, or EVENT_REGISTERED.
 */
public record ActivityLog(int logId, int userId, String username, String userRole, String actionType,
                          String description, Integer eventId, Integer registrationId, String timestamp) {
}
