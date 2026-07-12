package com.chargesquare.station.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Bir connector'ın fiyatlandırması: kWh başına fiyat artı opsiyonel sabit oturum başlangıç ücreti. */
@Entity
@Table(name = "tariffs")
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Para her zaman decimal'dir, asla float değil — NUMERIC(12,2) kolonlarına ve Session Service'teki Money'ye bak.
    @Column(name = "price_per_kwh", nullable = false)
    private BigDecimal pricePerKwh;

    @Column(name = "start_fee", nullable = false)
    private BigDecimal startFee;

    @Column(nullable = false)
    private String currency;

    protected Tariff() {
        // JPA için gerekli.
    }

    public Tariff(BigDecimal pricePerKwh, BigDecimal startFee, String currency) {
        this.pricePerKwh = pricePerKwh;
        this.startFee = startFee;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPricePerKwh() {
        return pricePerKwh;
    }

    public BigDecimal getStartFee() {
        return startFee;
    }

    public String getCurrency() {
        return currency;
    }
}
