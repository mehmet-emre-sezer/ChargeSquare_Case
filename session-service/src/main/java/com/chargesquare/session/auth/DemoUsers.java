package com.chargesquare.session.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Demo kullanıcıları tutar ve kimlik doğrular. Şifreler başlangıçta BCrypt ile hash'lenir;
 * hiçbir yerde açık şifre saklanmaz. Gerçek bir sistemde yerini kalıcı bir kullanıcı deposu alır.
 */
@Component
public class DemoUsers {

    private final Map<String, Account> byUsername = new HashMap<>();
    private final PasswordEncoder passwordEncoder;

    public DemoUsers(DemoUserProperties properties, PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        for (DemoUserProperties.Entry entry : properties.getUsers()) {
            byUsername.put(entry.username(),
                    new Account(entry.username(), passwordEncoder.encode(entry.password()), entry.role()));
        }
    }

    /** Kullanıcı adı + şifre eşleşirse hesabı döner; aksi halde boş. */
    public Optional<Account> authenticate(String username, String rawPassword) {
        Account account = byUsername.get(username);
        if (account == null || !passwordEncoder.matches(rawPassword, account.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(account);
    }

    public record Account(String username, String passwordHash, String role) {
    }
}
