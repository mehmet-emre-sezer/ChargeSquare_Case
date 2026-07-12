package com.chargesquare.station.web.error;

import com.chargesquare.station.domain.ConnectorNotAvailableException;
import com.chargesquare.station.domain.ConnectorNotFoundException;
import com.chargesquare.station.domain.StationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Beklenen domain hatalarını doğru status koduyla ortak hata gövdesine çevirir,
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

    @ExceptionHandler(StationNotFoundException.class)
    public ResponseEntity<ApiError> handleStationNotFound(StationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("STATION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ConnectorNotAvailableException.class)
    public ResponseEntity<ApiError> handleConnectorOccupied(ConnectorNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONNECTOR_OCCUPIED", ex.getMessage()));
    }

    // Sayısal olmayan path değişkeni (ör. /connectors/abc) bir bad request'tir, 500 değil.
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
