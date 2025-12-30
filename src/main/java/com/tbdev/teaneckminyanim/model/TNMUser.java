package com.tbdev.teaneckminyanim.model;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.tools.IDGenerator;
import lombok.*;

import jakarta.persistence.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "ACCOUNT")
public class TNMUser {

    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;

    @Column(name = "USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name = "EMAIL", nullable = false)
    private String email;

    @Column(name = "ENCRYPTED_PASSWORD", nullable = false)
    private String encryptedPassword;

    @Column(name = "ORGANIZATION_ID")
    private String organizationId;

    @Column(name = "ROLE_ID")
    private Integer roleId;

    public Role role() {
        return Role.getRole(roleId);
    }

    public boolean isSuperAdmin() {
        return (this.role() == Role.ADMIN && this.getOrganizationId() == null);
    }

    public boolean isAdmin() {
        return (this.role() == Role.ADMIN);
    }

    public boolean isUser() {
        return (this.role() == Role.USER);
    }

    public String getRoleDisplayName() {
        return switch (this.role()) {
            case ADMIN -> (this.getOrganizationId() == null) ? "Admin" : "Manager";
            case USER -> "User";
        };
    }
}