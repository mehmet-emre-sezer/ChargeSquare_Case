package com.chargesquare.session;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chargesquare.session.client.ConnectorSnapshot;
import com.chargesquare.session.client.StationClient;
import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start -> stop yaşam döngüsünü ve guard'ları gömülü gerçek Postgres'e karşı, controller
 * katmanından uçtan uca sınar. Tek dış bağımlılık olan Station Service mock'lanır. Her test
 * transaction'da koşar ve sonunda geri alınır; böylece testler birbirinden bağımsız kalır.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@Transactional
@TestPropertySource(properties = "station.service.url=http://localhost:8081")
class SessionFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StationClient stationClient;

    private void stubAvailableConnector() {
        when(stationClient.getConnector(anyLong())).thenReturn(
                new ConnectorSnapshot("AVAILABLE",
                        new ConnectorSnapshot.TariffView(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY")));
        // occupy/release void -> Mockito varsayılan olarak no-op.
    }

    private long startSession() throws Exception {
        String body = mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"connectorId\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.tariffSnapshot.pricePerKwh").value(8.50))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }

    @Test
    void startThenStop_chargesWalletAndCompletesSession() throws Exception {
        stubAvailableConnector();
        long sessionId = startSession();

        // Seed cüzdan 500.00; 12.5 kWh -> 108.25 -> bakiye 391.75.
        mockMvc.perform(post("/sessions/" + sessionId + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"energyKwh\":12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cost").value(108.25))
                .andExpect(jsonPath("$.walletBalanceAfter").value(391.75));

        mockMvc.perform(get("/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.cost").value(108.25));
    }

    @Test
    void stopTwice_secondReturns409() throws Exception {
        stubAvailableConnector();
        long sessionId = startSession();

        mockMvc.perform(post("/sessions/" + sessionId + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"energyKwh\":12.5}"))
                .andExpect(status().isOk());

        // İkinci stop artık ACTIVE olmadığı için reddedilir; ikinci kez faturalanmaz.
        mockMvc.perform(post("/sessions/" + sessionId + "/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"energyKwh\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    void startOnOccupiedConnector_returns409_andCreatesNoSession() throws Exception {
        when(stationClient.getConnector(anyLong())).thenReturn(
                new ConnectorSnapshot("OCCUPIED",
                        new ConnectorSnapshot.TariffView(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY")));

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":7,\"connectorId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONNECTOR_OCCUPIED"));

        // Occupy hiç çağrılmadı -> connector'a dokunulmadı ve oturum yaratılmadı.
        verify(stationClient, never()).occupy(anyLong());
    }

    @Test
    void startMissingUserId_returns400() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
