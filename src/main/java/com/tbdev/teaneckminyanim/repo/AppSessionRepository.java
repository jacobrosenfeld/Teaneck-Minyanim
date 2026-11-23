package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.AppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {
    Optional<AppSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AppSession> findByRefreshTokenHash(String refreshTokenHash);

    List<AppSession> findByFamilyId(String familyId);

    List<AppSession> findByAccountId(String accountId);

    @Query("""
            SELECT s FROM AppSession s
            WHERE s.revokedAt IS NULL AND s.refreshExpiresAt > :now
            ORDER BY s.lastUsedAt DESC, s.createdAt DESC
            """)
    List<AppSession> findActive(@Param("now") LocalDateTime now);

    /**
     * Claims a session for refresh rotation. Exactly one concurrent caller can change the row, so only that
     * caller may issue the replacement pair.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AppSession s SET s.rotatedAt = :now, s.revokedAt = :now, s.revokedReason = 'ROTATED'
            WHERE s.id = :id AND s.rotatedAt IS NULL AND s.revokedAt IS NULL
            """)
    int claimForRotation(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AppSession s SET s.revokedAt = :now, s.revokedReason = :reason
            WHERE s.familyId = :familyId AND s.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now,
                     @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE AppSession s SET s.revokedAt = :now, s.revokedReason = :reason
            WHERE s.accountId = :accountId AND s.revokedAt IS NULL
            """)
    int revokeAllActiveForAccount(@Param("accountId") String accountId, @Param("now") LocalDateTime now,
                                  @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AppSession s SET s.lastUsedAt = :now WHERE s.id = :id")
    int markUsed(@Param("id") String id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM AppSession s WHERE s.accountId = :accountId")
    int deleteAllForAccount(@Param("accountId") String accountId);

    /** Deletes rows revoked, or with an expired refresh token, before {@code cutoff}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM AppSession s WHERE s.revokedAt < :cutoff OR s.refreshExpiresAt < :cutoff")
    int purgeEndedBefore(@Param("cutoff") LocalDateTime cutoff);
}
