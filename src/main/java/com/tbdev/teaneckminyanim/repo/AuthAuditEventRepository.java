package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditEventRepository extends JpaRepository<AuthAuditEvent, String> {
    long deleteByAccountId(String accountId);
}
