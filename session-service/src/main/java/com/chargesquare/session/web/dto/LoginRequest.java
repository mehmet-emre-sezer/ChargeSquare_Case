package com.chargesquare.session.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Login istek gövdesi. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
