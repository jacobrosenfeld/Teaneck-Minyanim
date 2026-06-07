package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.MagicLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, String> {
    Optional<MagicLinkToken> findByTokenHash(String tokenHash);
}
