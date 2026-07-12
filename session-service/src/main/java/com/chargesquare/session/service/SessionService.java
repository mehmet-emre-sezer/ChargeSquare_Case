package com.chargesquare.session.service;

import com.chargesquare.session.client.ConnectorNotAvailableException;
import com.chargesquare.session.client.ConnectorSnapshot;
import com.chargesquare.session.client.StationClient;
import com.chargesquare.session.domain.Session;
import com.chargesquare.session.domain.SessionNotFoundException;
import com.chargesquare.session.domain.Wallet;
import com.chargesquare.session.domain.WalletNotFoundException;
import com.chargesquare.session.repository.SessionRepository;
import com.chargesquare.session.repository.WalletRepository;
import com.chargesquare.session.web.dto.SessionReceipt;
import com.chargesquare.session.web.dto.StartedSessionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Şarj oturumlarının start/stop yaşam döngüsü — işin kalbi.
 * Start'ta Station'ı doğrular ve tarifeyi dondurur; stop'ta maliyeti hesaplar,
 * cüzdanı ayarlar ve connector'ı serbest bırakır.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessions;
    private final WalletRepository wallets;
    private final StationClient stationClient;
    private final Clock clock;

    public SessionService(SessionRepository sessions,
                          WalletRepository wallets,
                          StationClient stationClient,
                          Clock clock) {
        this.sessions = sessions;
        this.wallets = wallets;
        this.stationClient = stationClient;
        this.clock = clock;
    }

    /**
     * START: cüzdanı doğrula, Station'dan connector'ı oku (404/409 ise session yaratma),
     * connector'ı occupy et, tarife snapshot'ıyla ACTIVE oturum oluştur.
     */
    @Transactional
    public StartedSessionResponse start(Long userId, Long connectorId) {
        // Faturalanamayacak bir oturum başlatmamak için cüzdanı erkenden doğrula (fail-fast).
        if (!wallets.existsByUserId(userId)) {
            throw new WalletNotFoundException(userId);
        }

        // Start yolundaki gerçek senkron çağrı: status + tarife.
        ConnectorSnapshot connector = stationClient.getConnector(connectorId);
        if (!connector.isAvailable()) {
            throw new ConnectorNotAvailableException(connectorId);
        }

        // Connector'ı kap; yarış durumunda (bu arada dolduysa) Station 409 döner ve oturum yaratılmaz.
        stationClient.occupy(connectorId);

        Session session = Session.start(userId, connectorId, connector.toTariffSnapshot(), clock.instant());
        sessions.save(session);
        log.info("Session {} started (user {}, connector {})", session.getId(), userId, connectorId);
        return StartedSessionResponse.from(session);
    }

    /**
     * STOP: oturumu kilitle ve ACTIVE olduğunu guard'la, maliyeti snapshot'tan hesapla,
     * cüzdanı ayarla, COMPLETED işaretle ve connector'ı serbest bırak.
     * Release bu transaction içinde çağrılır; Station düşükse tüm işlem geri alınır ve 503 döner
     * (borç yazılmaz, connector kilitli kalmaz — temiz, tekrar denenebilir durum).
     */
    @Transactional
    public SessionReceipt stop(Long sessionId, BigDecimal energyKwh) {
        Session session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Maliyeti hesaplar, COMPLETED işaretler; ACTIVE değilse (double-stop dahil) 409 fırlatır.
        BigDecimal cost = session.stop(energyKwh, Instant.now(clock));

        Wallet wallet = wallets.findByUserIdForUpdate(session.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(session.getUserId()));
        BigDecimal balanceAfter = wallet.debit(cost);
        session.recordSettlement(balanceAfter);
        log.info("Session {} stopped: charged {} {}, wallet balance {}",
                sessionId, cost, wallet.getCurrency(), balanceAfter);

        // Connector'ı serbest bırak. Başarısız olursa exception yayılır ve transaction geri alınır.
        stationClient.release(session.getConnectorId());

        return SessionReceipt.from(session);
    }

    @Transactional(readOnly = true)
    public SessionReceipt getSession(Long sessionId) {
        Session session = sessions.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return SessionReceipt.from(session);
    }

    @Transactional(readOnly = true)
    public List<SessionReceipt> getUserSessions(Long userId) {
        return sessions.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(SessionReceipt::from)
                .toList();
    }
}
