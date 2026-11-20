package com.returnright.core.config;

import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = "com.returnright", considerNestedRepositories = true)
public class CoreConfig {

    @Bean
    public AppClock appClock() {
        return new AppClock();
    }

    @Bean
    public ZoneId businessZone(RrProperties props) {
        return ZoneId.of(props.businessTz());
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
