package com.chargesquare.session.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Station Service'e giden RestClient. Base URL ortamdan gelir ve timeout'lar tanımlıdır;
 * böylece Station yavaşlar/düşerse çağrı asılı kalmaz, fail-fast eder.
 */
@Configuration
public class StationClientConfig {

    @Bean
    public RestClient stationRestClient(RestClient.Builder builder,
                                        @Value("${station.service.url}") String stationServiceUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(5));
        return builder
                .baseUrl(stationServiceUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
