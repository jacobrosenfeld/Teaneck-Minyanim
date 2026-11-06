package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, String> {
    Optional<MagicLinkToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MagicLinkToken t SET t.usedAt = :usedAt "
            + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :usedAt")
    int consumeIfUsable(@Param("id") String id, @Param("usedAt") LocalDateTime usedAt);

    long deleteByExpiresAtBeforeOrUsedAtBefore(LocalDateTime expiredBefore, LocalDateTime usedBefore);

    long deleteByAccountId(String accountId);
}
