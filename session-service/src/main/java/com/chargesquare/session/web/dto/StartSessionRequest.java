package com.chargesquare.session.web.dto;

import jakarta.validation.constraints.NotNull;

/** START isteği gövdesi. Eksik alanlar sınırda reddedilir (400 VALIDATION_ERROR). */
public record StartSessionRequest(
        @NotNull Long userId,
        @NotNull Long connectorId
) {
}
