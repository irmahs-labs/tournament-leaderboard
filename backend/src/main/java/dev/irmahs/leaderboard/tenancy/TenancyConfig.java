package dev.irmahs.leaderboard.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

/** Replaces Spring Boot's transaction manager with one that passes the organisation scope on. */
@Configuration
class TenancyConfig {

    @Bean
    JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new OrganizationScopedTransactionManager(entityManagerFactory);
    }
}
