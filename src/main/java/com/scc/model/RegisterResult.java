package com.scc.model;

/** The result of a student registering for an event. registrationId is 0 unless it worked. */
public record RegisterResult(Outcome outcome, int registrationId) {

    public enum Outcome {
        REGISTERED,
        EVENT_NOT_FOUND,
        ALREADY_REGISTERED,
        EVENT_FULL
    }
}