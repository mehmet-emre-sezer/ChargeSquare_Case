package com.chargesquare.session.client;

import com.chargesquare.session.domain.TariffSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Station Service'in connector yanıtından okuduğumuz kısım: status ve tarife.
 * Dış sözleşme değişkenliğine dayanıklı olmak için bilinmeyen alanlar yok sayılır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectorSnapshot(String status, TariffView tariff) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TariffView(BigDecimal pricePerKwh, BigDecimal startFee, String currency) {
    }

    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }

    /** Station'dan gelen tarifeyi, oturumda dondurulacak snapshot'a çevirir. */
    public TariffSnapshot toTariffSnapshot() {
        return new TariffSnapshot(tariff.pricePerKwh(), tariff.startFee(), tariff.currency());
    }
}
