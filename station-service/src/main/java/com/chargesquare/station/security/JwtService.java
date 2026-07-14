package com.chargesquare.station.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gelen JWT'leri doğrular. Station token üretmez; Session Service ile paylaşılan
 * simetrik (HMAC) anahtarla token'ı yerel olarak doğrular.
 */
@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** İmzayı ve süreyi doğrular, geçerliyse claim'leri döner. Geçersizse exception fırlatır. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String roleOf(Claims claims) {
        return claims.get(ROLE_CLAIM, String.class);
    }
}
