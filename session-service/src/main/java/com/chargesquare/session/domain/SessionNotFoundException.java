package com.chargesquare.session.domain;

/** Bir oturum id'si mevcut olmadığında fırlatılır. HTTP 404'e eşlenir. */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(Long sessionId) {
        super("Session " + sessionId + " not found");
    }
}
