package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.ApiWebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ApiWebAuthnChallengeRepository extends JpaRepository<ApiWebAuthnChallenge, String> {

    /**
     * Marks the challenge used if it is unused and unexpired. Exactly one caller can get 1 back, so a challenge
     * is single-use even under concurrent verifies. Runs (and commits) in its own transaction so a failed
     * verification afterwards cannot roll the consumption back.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ApiWebAuthnChallenge c SET c.usedAt = :now "
            + "WHERE c.id = :id AND c.usedAt IS NULL AND c.expiresAt > :now")
    int consumeIfUsable(@Param("id") String id, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM ApiWebAuthnChallenge c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
