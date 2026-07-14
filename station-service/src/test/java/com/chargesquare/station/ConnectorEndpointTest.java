package com.chargesquare.station;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connector endpoint'lerini seed veriye karşı sınar (gömülü gerçek Postgres). Uçlar artık
 * korumalı olduğu için isteklere JWT eklenir; token'lar test içinde paylaşılan secret'la üretilir.
 * Her test transaction'da koşup geri alınır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@Transactional
@TestPropertySource(properties = "jwt.secret=" + ConnectorEndpointTest.SECRET)
class ConnectorEndpointTest {

    static final String SECRET = "test-secret-key-that-is-long-enough-0123456789";

    @Autowired
    private MockMvc mockMvc;

    private static String bearer(String role) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("test")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }

    @Test
    void getConnector_returnsStatusAndTariff() throws Exception {
        mockMvc.perform(get("/connectors/10").header(HttpHeaders.AUTHORIZATION, bearer("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.tariff.pricePerKwh").value(8.50));
    }

    @Test
    void getUnknownConnector_returns404() throws Exception {
        mockMvc.perform(get("/connectors/999").header(HttpHeaders.AUTHORIZATION, bearer("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CONNECTOR_NOT_FOUND"));
    }

    @Test
    void occupyTwice_secondReturns409() throws Exception {
        mockMvc.perform(post("/connectors/10/occupy").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));

        mockMvc.perform(post("/connectors/10/occupy").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONNECTOR_OCCUPIED"));
    }

    @Test
    void read_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/connectors/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void occupy_withViewerRole_returns403() throws Exception {
        mockMvc.perform(post("/connectors/10/occupy").header(HttpHeaders.AUTHORIZATION, bearer("VIEWER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
