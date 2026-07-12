package com.chargesquare.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Bir kullanıcının önden ödemeli bakiyesi. Bakiye geçişlerine kendisi sahiptir.
 * Karar: enerji zaten teslim edildiği için stop asla reddedilmez; yetersiz bakiyede
 * borç kabul edilir ve bakiye negatife düşebilir (bkz. DESIGN.md).
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false)
    private String currency;

    protected Wallet() {
        // JPA için gerekli.
    }

    public Wallet(Long userId, BigDecimal balance, String currency) {
        this.userId = userId;
        this.balance = balance;
        this.currency = currency;
    }

    /** Tutarı bakiyeden düşer ve yeni bakiyeyi döndürür. Negatif bakiyeye bilerek izin verilir. */
    public BigDecimal debit(BigDecimal amount) {
        balance = balance.subtract(amount);
        return balance;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }
}
