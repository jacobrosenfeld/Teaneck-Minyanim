package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.model.Notification;
import com.tbdev.teaneckminyanim.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;

    /**
     * Get all active banner notifications
     */
    public List<Notification> getActiveBanners() {
        return repository.findActiveByType("BANNER").stream()
                .filter(Notification::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Get all active popup notifications
     */
    public List<Notification> getActivePopups() {
        return repository.findActiveByType("POPUP").stream()
                .filter(Notification::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Get all notifications of a specific type
     */
    public List<Notification> getByType(String type) {
        return repository.findByTypeOrderByCreatedAtDesc(type);
    }

    /**
     * Get all notifications
     */
    public List<Notification> getAll() {
        return repository.findAll();
    }

    /**
     * Find notification by ID
     */
    public Notification findById(String id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * Create or update a notification
     */
    @Transactional
    public Notification save(Notification notification) {
        return repository.save(notification);
    }

    /**
     * Delete a notification
     */
    @Transactional
    public void delete(String id) {
        repository.deleteById(id);
    }

    /**
     * Toggle notification enabled status
     */
    @Transactional
    public boolean toggleEnabled(String id) {
        Notification notification = findById(id);
        if (notification != null) {
            notification.setEnabled(!notification.getEnabled());
            repository.save(notification);
            return notification.getEnabled();
        }
        return false;
    }

    /**
     * Create a new banner notification
     */
    public Notification createBanner(String title, String message, LocalDateTime expirationDate, Integer maxDisplays) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType("BANNER");
        notification.setEnabled(true);
        notification.setExpirationDate(expirationDate);
        notification.setMaxDisplays(maxDisplays);
        return save(notification);
    }

    /**
     * Create a new popup notification
     */
    public Notification createPopup(String title, String message, LocalDateTime expirationDate, Integer maxDisplays) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType("POPUP");
        notification.setEnabled(true);
        notification.setExpirationDate(expirationDate);
        notification.setMaxDisplays(maxDisplays);
        return save(notification);
    }
}
