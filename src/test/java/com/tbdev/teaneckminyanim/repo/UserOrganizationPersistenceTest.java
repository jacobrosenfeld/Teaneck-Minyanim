package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.TNMUser;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserOrganizationPersistenceTest {
    @Test
    void persistsAndRemovesAdditionalAssignmentsWithoutLosingLegacyPrimary() {
        var configuration = new Configuration().addAnnotatedClass(TNMUser.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:assignments;MODE=MariaDB")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false");
        try (var factory = configuration.buildSessionFactory()) {
            try (var session = factory.openSession()) {
                var tx = session.beginTransaction();
                TNMUser user = TNMUser.builder().id("manager").username("manager").email("manager@example.com")
                        .encryptedPassword("!PASSWORDLESS").roleId(1).organizationId("one").build();
                user.getAdditionalOrganizationIds().add("two");
                session.persist(user);
                tx.commit();
            }
            try (var session = factory.openSession()) {
                var tx = session.beginTransaction();
                TNMUser user = session.find(TNMUser.class, "manager");
                assertEquals(Set.of("one", "two"), user.getAccessibleOrganizationIds());
                var assigned = session.createQuery("SELECT DISTINCT u FROM TNMUser u LEFT JOIN u.additionalOrganizationIds orgId "
                        + "WHERE u.organizationId = :organizationId OR orgId = :organizationId", TNMUser.class)
                        .setParameter("organizationId", "two").getResultList();
                assertEquals(1, assigned.size());
                user.getAdditionalOrganizationIds().clear();
                tx.commit();
            }
            try (var session = factory.openSession()) {
                TNMUser user = session.find(TNMUser.class, "manager");
                assertEquals(Set.of("one"), user.getAccessibleOrganizationIds());
                assertFalse(user.isSuperAdmin());
                assertEquals(0L, session.createNativeQuery("SELECT COUNT(*) FROM ACCOUNT_ORGANIZATION", Long.class).getSingleResult());
            }
        }
    }
}
