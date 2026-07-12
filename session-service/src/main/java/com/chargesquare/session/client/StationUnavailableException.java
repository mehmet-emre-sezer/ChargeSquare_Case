package com.chargesquare.session.client;

/**
 * Station Service'e ulaşılamadığında veya beklenmeyen bir hata döndüğünde fırlatılır.
 * Fail-fast: HTTP 503'e eşlenir (retry/fallback yok — bkz. DESIGN.md).
 */
public class StationUnavailableException extends RuntimeException {

    public StationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
