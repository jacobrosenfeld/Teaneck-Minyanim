package com.tbdev.teaneckminyanim.model;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity
@Data
@Table(name = "ACCOUNT")
public class Account {
    @Id
    @Column(name = "ID")
    private String id;

    @Column(name = "USERNAME")
    private String username;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "ENCRYPTED_PASSWORD")
    private String encryptedPassword;

    @Column(name = "ORGANIZATION_ID")
    private String organizationId;

    @Column(name = "ROLE_ID")
    private String roleId;

    @Column(name = "WEEKLY_MINYAN_REVIEW_EMAILS_ENABLED")
    private Boolean weeklyMinyanReviewEmailsEnabled;
}
