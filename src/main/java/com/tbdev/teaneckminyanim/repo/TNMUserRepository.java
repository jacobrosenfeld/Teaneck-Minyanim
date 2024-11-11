package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.structure.TNMUser;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TNMUserRepository extends JpaRepository<TNMUser, String> {
    Optional<TNMUser> findByUsername(String username);
}
