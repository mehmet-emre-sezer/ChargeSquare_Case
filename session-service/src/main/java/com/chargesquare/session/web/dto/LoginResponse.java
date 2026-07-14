package com.chargesquare.session.web.dto;

/** Login yanıtı: taşınacak JWT ve kullanıcının rolü. */
public record LoginResponse(
        String token,
        String role
) {
}
