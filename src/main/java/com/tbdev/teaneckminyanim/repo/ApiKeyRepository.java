package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findAllByOrderByCreatedAtDesc();

    List<ApiKey> findByAccountIdOrderByCreatedAtDesc(String accountId);

    /** Derived delete: loads and removes each key so its scope and shul rows go too. */
    @Transactional
    long deleteByAccountId(String accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE ApiKey k SET k.revokedAt = :now, k.revokedByAccountId = :actor, k.revokedReason = :reason
            WHERE k.accountId = :accountId AND k.revokedAt IS NULL
            """)
    int revokeAllActiveForAccount(@Param("accountId") String accountId,
                                  @Param("now") LocalDateTime now,
                                  @Param("actor") String actorAccountId,
                                  @Param("reason") String reason);

    /** Records key use; skipped when the key was revoked in the meantime. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :now, k.lastUsedIpHash = :ipHash WHERE k.id = :id AND k.revokedAt IS NULL")
    int markUsed(@Param("id") String id, @Param("now") LocalDateTime now, @Param("ipHash") String ipHash);
}
