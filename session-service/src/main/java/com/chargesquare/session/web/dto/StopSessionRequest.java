package com.chargesquare.session.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** STOP isteği gövdesi. Metre teslim edilen enerjiyi bildirir; eksik/negatif ise 400. */
public record StopSessionRequest(
        @NotNull @PositiveOrZero BigDecimal energyKwh
) {
}
