package com.chargesquare.session.web.dto;

import com.chargesquare.session.domain.TariffSnapshot;
import java.math.BigDecimal;

/** START yanıtında dönen, oturuma dondurulmuş tarife. */
public record TariffSnapshotView(
        BigDecimal pricePerKwh,
        BigDecimal startFee,
        String currency
) {
    public static TariffSnapshotView from(TariffSnapshot snapshot) {
        return new TariffSnapshotView(
                snapshot.getPricePerKwh(),
                snapshot.getStartFee(),
                snapshot.getCurrency()
        );
    }
}
