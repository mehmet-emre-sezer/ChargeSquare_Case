package com.chargesquare.station.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tek bir şarj noktası (EVSE). Status geçişlerine kendisi sahiptir; böylece AVAILABLE/OCCUPIED
 * invariant'ı dışarıdan set edilmek yerine verinin yanında durur.
 */
@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;

    @Column(nullable = false)
    private String type;

    @Column(name = "power_kw", nullable = false)
    private Integer powerKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectorStatus status;

    protected Connector() {
        // JPA için gerekli.
    }

    public Connector(Station station, Tariff tariff, String type, Integer powerKw, ConnectorStatus status) {
        this.station = station;
        this.tariff = tariff;
        this.type = type;
        this.powerKw = powerKw;
        this.status = status;
    }

    /** Connector'ı kullanımda olarak işaretler. Şu an müsait değilse reddedilir. */
    public void occupy() {
        if (status != ConnectorStatus.AVAILABLE) {
            throw new ConnectorNotAvailableException(id);
        }
        status = ConnectorStatus.OCCUPIED;
    }

    /** Connector'ı serbest bırakır. Idempotent: zaten müsait bir connector'ı bırakmak no-op'tur. */
    public void release() {
        status = ConnectorStatus.AVAILABLE;
    }

    public boolean isAvailable() {
        return status == ConnectorStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public Tariff getTariff() {
        return tariff;
    }

    public String getType() {
        return type;
    }

    public Integer getPowerKw() {
        return powerKw;
    }

    public ConnectorStatus getStatus() {
        return status;
    }
}
