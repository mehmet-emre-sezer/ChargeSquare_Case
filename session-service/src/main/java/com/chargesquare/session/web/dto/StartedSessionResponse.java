package com.chargesquare.session.web.dto;

import com.chargesquare.session.domain.Session;
import java.time.Instant;

/** START (201) yanıtı: yeni ACTIVE oturum ve dondurulan tarife. */
public record StartedSessionResponse(
        Long sessionId,
        Long userId,
        Long connectorId,
        String status,
        Instant startedAt,
        TariffSnapshotView tariffSnapshot
) {
    public static StartedSessionResponse from(Session session) {
        return new StartedSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getConnectorId(),
                session.getStatus().name(),
                session.getStartedAt(),
                TariffSnapshotView.from(session.getTariffSnapshot())
        );
    }
}
