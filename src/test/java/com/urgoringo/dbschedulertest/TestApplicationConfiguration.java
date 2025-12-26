package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Clock;
import com.github.kagkarlsson.scheduler.testhelper.SettableClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestApplicationConfiguration {

    @Bean
    public SettableClock testClock() {
        return new SettableClock();
    }
}
