package com.chargesquare.station.web.dto;

import com.chargesquare.station.domain.Tariff;
import java.math.BigDecimal;

/** API üzerinden sunulan haliyle tarife. */
public record TariffView(
        Long tariffId,
        BigDecimal pricePerKwh,
        BigDecimal startFee,
        String currency
) {
    public static TariffView from(Tariff tariff) {
        return new TariffView(
                tariff.getId(),
                tariff.getPricePerKwh(),
                tariff.getStartFee(),
                tariff.getCurrency()
        );
    }
}
