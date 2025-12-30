package com.tbdev.teaneckminyanim.repo;

import com.tbdev.teaneckminyanim.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    
    /**
     * Find all active notifications of a specific type
     */
    @Query("SELECT n FROM Notification n WHERE n.enabled = true AND n.type = ?1 AND (n.expirationDate IS NULL OR n.expirationDate > CURRENT_TIMESTAMP) ORDER BY n.createdAt DESC")
    List<Notification> findActiveByType(String type);
    
    /**
     * Find all notifications of a specific type (regardless of status)
     */
    List<Notification> findByTypeOrderByCreatedAtDesc(String type);
    
    /**
     * Find all enabled notifications
     */
    List<Notification> findByEnabledOrderByCreatedAtDesc(Boolean enabled);
}
