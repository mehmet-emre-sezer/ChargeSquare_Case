package com.chargesquare.session.web.error;

import com.chargesquare.session.client.ConnectorNotAvailableException;
import com.chargesquare.session.client.ConnectorNotFoundException;
import com.chargesquare.session.client.StationUnavailableException;
import com.chargesquare.session.domain.SessionNotActiveException;
import com.chargesquare.session.domain.SessionNotFoundException;
import com.chargesquare.session.domain.WalletNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Beklenen domain/istek hatalarını doğru status koduyla ortak hata gövdesine çevirir,
 * beklenmeyen hatalarda ise iç detayların çağırana sızmasını engeller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConnectorNotFoundException.class)
    public ResponseEntity<ApiError> handleConnectorNotFound(ConnectorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CONNECTOR_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ApiError> handleSessionNotFound(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("SESSION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ApiError> handleWalletNotFound(WalletNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("WALLET_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ConnectorNotAvailableException.class)
    public ResponseEntity<ApiError> handleConnectorOccupied(ConnectorNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONNECTOR_OCCUPIED", ex.getMessage()));
    }

    @ExceptionHandler(SessionNotActiveException.class)
    public ResponseEntity<ApiError> handleSessionNotActive(SessionNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("SESSION_NOT_ACTIVE", ex.getMessage()));
    }

    // Station'a ulaşılamıyor: fail-fast, 503.
    @ExceptionHandler(StationUnavailableException.class)
    public ResponseEntity<ApiError> handleStationUnavailable(StationUnavailableException ex) {
        log.warn("Station Service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("STATION_UNAVAILABLE", "Station Service is unavailable"));
    }

    // Gövde doğrulama hatası (eksik userId, negatif energyKwh vb.) -> 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", "Request validation failed"));
    }

    // Okunamayan/bozuk JSON gövdesi -> 400.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", "Malformed request body"));
    }

    // Sayısal olmayan path değişkeni (ör. /sessions/abc) -> 400.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", "Invalid value for '" + ex.getName() + "'"));
    }

    // Beklenmeyen her şey: detayı sunucu tarafında logla, çağırana güvenli genel bir gövde dön.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "Unexpected error"));
    }
}
