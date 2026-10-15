package io.github.ronaldobertolucci.unita.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class InvestmentClockConfig {
    @Bean
    public Clock investmentClock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
