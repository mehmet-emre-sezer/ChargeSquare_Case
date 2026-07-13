package com.chargesquare.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Cost hesabının davranış testleri — DB veya Spring context gerektirmez. */
class TariffSnapshotTest {

    @Test
    void costFor_workedExample() {
        // Spec'teki örnek: 12.5 kWh × 8.50 + 2.00 = 108.25
        TariffSnapshot tariff = new TariffSnapshot(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY");

        assertThat(tariff.costFor(new BigDecimal("12.5"))).isEqualByComparingTo("108.25");
    }

    @Test
    void costFor_roundsHalfUpToTwoDecimals() {
        // 12.567 × 8.50 = 106.8195 (+2.00) = 108.8195 -> HALF_UP 2 hane -> 108.82
        TariffSnapshot tariff = new TariffSnapshot(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY");

        assertThat(tariff.costFor(new BigDecimal("12.567"))).isEqualByComparingTo("108.82");
    }

    @Test
    void costFor_halfUpBoundaryRoundsUp() {
        // 1.005 × 1.00 = 1.005 -> HALF_UP -> 1.01 (naive float burada aşağı yuvarlardı)
        TariffSnapshot tariff = new TariffSnapshot(new BigDecimal("1.00"), new BigDecimal("0.00"), "TRY");

        assertThat(tariff.costFor(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
    }

    @Test
    void costFor_zeroEnergy_isJustStartFee() {
        TariffSnapshot tariff = new TariffSnapshot(new BigDecimal("8.50"), new BigDecimal("2.00"), "TRY");

        assertThat(tariff.costFor(BigDecimal.ZERO)).isEqualByComparingTo("2.00");
    }
}
