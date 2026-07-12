package com.chargesquare.session.web.dto;

import com.chargesquare.session.domain.Session;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * STOP (200) ve okuma (GET) yanıtı — aynı makbuz shape'i.
 * ACTIVE bir oturumda henüz ayarlanmamış alanlar (endedAt, cost, walletBalanceAfter) null döner.
 */
public record SessionReceipt(
        Long sessionId,
        Long userId,
        Long connectorId,
        String status,
        Instant startedAt,
        Instant endedAt,
        BigDecimal energyKwh,
        BigDecimal cost,
        String currency,
        BigDecimal walletBalanceAfter
) {
    public static SessionReceipt from(Session session) {
        return new SessionReceipt(
                session.getId(),
                session.getUserId(),
                session.getConnectorId(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getEnergyKwh(),
                session.getCost(),
                session.getTariffSnapshot().getCurrency(),
                session.getWalletBalanceAfter()
        );
    }
}
