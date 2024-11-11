package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.structure.TNMUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TNMUserService {
    private final TNMUserRepository repository;

    @Autowired
    public TNMUserService(TNMUserRepository repository) {
        this.repository = repository;
    }

    public TNMUser findByName(String username) {
        return repository.findByUsername(username).orElse(null);
    }
    public List<TNMUser> getAll() {
        return repository.findAll();
    }

    public TNMUser findById(String id) {
        return repository.findById(id).orElse(null);
    }

    public boolean save(TNMUser tnmUser) {
        try {
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean update(TNMUser tnmUser) {
        try {
            repository.save(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    public boolean delete(TNMUser tnmUser) {
        try {
            repository.delete(tnmUser);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


}

