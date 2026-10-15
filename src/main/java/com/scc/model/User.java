package com.scc.model;

/**
 * A user account (record = a short way to write an unchangeable data class).
 * role:   STUDENT, STAFF (event staff), or ADMIN
 * status: PENDING (new account, waiting for approval), ACTIVE, or INACTIVE
 * studentNumber and course are only used by students (they are null for staff and admins).
 */
public record User(int userId, String fullName, String contactNumber, String username, String passwordHash,
                   String role, String accountStatus, String createdAt, String studentNumber, String course) {

    public static final String ROLE_STUDENT = "STUDENT";
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public boolean isStudent() { return role.equals(ROLE_STUDENT); }
    public boolean isStaff() { return role.equals(ROLE_STAFF); }
    public boolean isAdmin() { return role.equals(ROLE_ADMIN); }
    public boolean isActive() { return accountStatus.equals(STATUS_ACTIVE); }
    public boolean isPending() { return accountStatus.equals(STATUS_PENDING); }

    // A friendly role name for screens.
    public String roleLabel() {
        switch (role) {
            case ROLE_ADMIN:
                return "Admin";
            case ROLE_STAFF:
                return "Event Staff";
            default:
                return "Student";
        }
    }

    // Records cannot be changed, so these methods return a NEW User with one changed value.
    public User withFullName(String value) {
        return new User(userId, value, contactNumber, username, passwordHash, role, accountStatus, createdAt, studentNumber, course);
    }

    public User withContactNumber(String value) {
        return new User(userId, fullName, value, username, passwordHash, role, accountStatus, createdAt, studentNumber, course);
    }

    public User withCourse(String value) {
        return new User(userId, fullName, contactNumber, username, passwordHash, role, accountStatus, createdAt, studentNumber, value);
    }

    public User withPasswordHash(String value) {
        return new User(userId, fullName, contactNumber, username, value, role, accountStatus, createdAt, studentNumber, course);
    }

    // The password hash is never printed.
    @Override
    public String toString() {
        return "User[" + userId + ", " + username + ", " + role + ", " + accountStatus + "]";
    }
}