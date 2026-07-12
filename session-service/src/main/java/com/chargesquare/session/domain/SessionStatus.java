package com.chargesquare.session.domain;

/** Bir şarj oturumunun yaşam döngüsü: enerji akarken ACTIVE, durdurulup faturalanınca COMPLETED. */
public enum SessionStatus {
    ACTIVE,
    COMPLETED
}
