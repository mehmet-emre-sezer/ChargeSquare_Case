package com.chargesquare.session.domain;

/** ACTIVE olmayan bir oturumu durdurmaya çalışınca fırlatılır (double-stop dahil). HTTP 409'a eşlenir. */
public class SessionNotActiveException extends RuntimeException {

    public SessionNotActiveException(Long sessionId) {
        super("Session " + sessionId + " is not ACTIVE");
    }
}
