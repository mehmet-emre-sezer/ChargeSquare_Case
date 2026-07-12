package com.chargesquare.station.web.error;

/** API genelinde kullanılan tek ve tutarlı JSON hata gövdesi: { "error": ..., "message": ... }. */
public record ApiError(String error, String message) {
}
