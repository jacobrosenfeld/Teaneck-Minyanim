package com.tbdev.teaneckminyanim.model;

import lombok.Data;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Column;
import javax.persistence.Table;

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
}
