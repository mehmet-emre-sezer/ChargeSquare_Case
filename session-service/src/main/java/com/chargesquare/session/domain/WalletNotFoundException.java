package com.chargesquare.session.domain;

/** Bir kullanıcının cüzdanı bulunamadığında fırlatılır. HTTP 404'e eşlenir. */
public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(Long userId) {
        super("Wallet for user " + userId + " not found");
    }
}
