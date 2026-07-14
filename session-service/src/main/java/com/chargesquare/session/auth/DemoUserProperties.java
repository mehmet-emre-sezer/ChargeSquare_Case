package com.chargesquare.session.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml'deki demo kullanıcı listesini bağlar (gerçek kullanıcı deposu yerine). */
@ConfigurationProperties(prefix = "demo")
public class DemoUserProperties {

    private List<Entry> users = List.of();

    public List<Entry> getUsers() {
        return users;
    }

    public void setUsers(List<Entry> users) {
        this.users = users;
    }

    public record Entry(String username, String password, String role) {
    }
}
