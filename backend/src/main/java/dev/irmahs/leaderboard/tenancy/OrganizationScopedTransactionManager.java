package dev.irmahs.leaderboard.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Starts each transaction like Spring's own, then tells the database which organisation it's
 * for, when there is one. The setting ends with the transaction, so a pooled connection never
 * carries it into another request.
 */
class OrganizationScopedTransactionManager extends JpaTransactionManager {

    OrganizationScopedTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        OrganizationScope.current().ifPresent(organizationId -> {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(obtainEntityManagerFactory());
            em.createNativeQuery("SELECT set_config('app.organization_id', ?1, true)")
                    .setParameter(1, organizationId.toString())
                    .getSingleResult();
        });
    }
}
