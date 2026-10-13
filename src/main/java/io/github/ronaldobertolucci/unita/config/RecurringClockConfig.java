package io.github.ronaldobertolucci.unita.config;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration
public class RecurringClockConfig {
 @Bean("recurringClock") public Clock recurringClock() { return Clock.systemUTC(); }
}
