package com.chargesquare.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bir şarj oturumu. Start/stop yaşam döngüsüne kendisi sahiptir: geçiş guard'ları ve
 * cost hesabı buradadır, dışarıdan status set edilmez.
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "energy_kwh")
    private BigDecimal energyKwh;

    @Column(name = "cost")
    private BigDecimal cost;

    @Column(name = "wallet_balance_after")
    private BigDecimal walletBalanceAfter;

    @Embedded
    private TariffSnapshot tariffSnapshot;

    protected Session() {
        // JPA için gerekli.
    }

    private Session(Long userId, Long connectorId, TariffSnapshot tariffSnapshot, Instant startedAt) {
        this.userId = userId;
        this.connectorId = connectorId;
        this.tariffSnapshot = tariffSnapshot;
        this.startedAt = startedAt;
        this.status = SessionStatus.ACTIVE;
    }

    /** Tarife snapshot'ı dondurulmuş yeni bir ACTIVE oturum başlatır. */
    public static Session start(Long userId, Long connectorId, TariffSnapshot tariffSnapshot, Instant startedAt) {
        return new Session(userId, connectorId, tariffSnapshot, startedAt);
    }

    /**
     * Oturumu durdurur: maliyeti snapshot'tan hesaplar, COMPLETED işaretler ve maliyeti döndürür.
     * Yalnızca ACTIVE bir oturum durdurulabilir — tekrar durdurma (double-stop) burada engellenir.
     */
    public BigDecimal stop(BigDecimal energyKwh, Instant endedAt) {
        if (status != SessionStatus.ACTIVE) {
            throw new SessionNotActiveException(id);
        }
        this.energyKwh = energyKwh;
        this.cost = tariffSnapshot.costFor(energyKwh);
        this.endedAt = endedAt;
        this.status = SessionStatus.COMPLETED;
        return cost;
    }

    /** Cüzdan ayarlandıktan sonra oluşan bakiyeyi makbuz için kaydeder. */
    public void recordSettlement(BigDecimal walletBalanceAfter) {
        this.walletBalanceAfter = walletBalanceAfter;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public BigDecimal getWalletBalanceAfter() {
        return walletBalanceAfter;
    }

    public TariffSnapshot getTariffSnapshot() {
        return tariffSnapshot;
    }
}
