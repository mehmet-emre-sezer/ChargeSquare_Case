package com.chargesquare.session.auth;

/** Kullanıcı adı/şifre eşleşmediğinde fırlatılır. HTTP 401'e eşlenir. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
