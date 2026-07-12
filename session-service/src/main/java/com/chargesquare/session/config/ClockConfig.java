package com.chargesquare.session.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Zaman bir bağımlılık olarak enjekte edilir; böylece testler saati kontrol edebilir. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
