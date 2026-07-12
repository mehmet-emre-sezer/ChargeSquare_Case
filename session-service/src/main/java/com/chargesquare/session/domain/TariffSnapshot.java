package com.chargesquare.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Oturum başında dondurulan tarife. Immutable bir value object'tir ve fiyatlandırma
 * kuralı (cost hesabı) verinin yanında durur; böylece fiyat sonradan değişse bile
 * bu oturumun maliyeti sabit kalır.
 */
@Embeddable
public class TariffSnapshot {

    @Column(name = "price_per_kwh", nullable = false)
    private BigDecimal pricePerKwh;

    @Column(name = "start_fee", nullable = false)
    private BigDecimal startFee;

    @Column(nullable = false)
    private String currency;

    protected TariffSnapshot() {
        // JPA için gerekli.
    }

    public TariffSnapshot(BigDecimal pricePerKwh, BigDecimal startFee, String currency) {
        this.pricePerKwh = Objects.requireNonNull(pricePerKwh, "pricePerKwh");
        this.startFee = Objects.requireNonNull(startFee, "startFee");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /**
     * Bu oturumun maliyeti: energyKwh × pricePerKwh + startFee, 2 haneye HALF_UP yuvarlanmış.
     * Süre maliyeti etkilemez — yalnızca enerji ve fiyat.
     */
    public BigDecimal costFor(BigDecimal energyKwh) {
        BigDecimal energyCost = energyKwh.multiply(pricePerKwh);
        return energyCost.add(startFee).setScale(2, RoundingMode.HALF_UP);
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
