package com.tbdev.teaneckminyanim.admin.structure.user;

//import org.springframework.data.annotation.Id;

import com.tbdev.teaneckminyanim.admin.structure.TNMObject;
import com.tbdev.teaneckminyanim.admin.structure.IDGenerator;
import com.tbdev.teaneckminyanim.admin.structure.Role;

import javax.persistence.*;

@Table(name="ACCOUNT")
public class TNMUser extends TNMObject implements IDGenerator {
    @Column(name="USERNAME", nullable = false, unique = true)
    private String username;

    @Column(name="EMAIL", nullable = false)
    private String email;

    @Column(name="ENCRYPTED_PASSWORD", nullable = false)
    private String encryptedPassword;

    @Column(name="ORGANIZATION_ID")
    private String organizationId;

    @Column(name = "ROLE_ID")
    private Integer roleId;

    public Role role() {
        return Role.getRole(roleId);
    }

    public TNMUser(String id, String username, String email, String encryptedPassword, String organizationId, Integer role) {
        super.id = id;
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        if (organizationId != null && !organizationId.equals("null"))
            this.organizationId = organizationId;
        this.roleId = role;
    }

    public TNMUser(String username, String email, String encryptedPassword, String organizationId, Integer role) {
        super.id = generateID('A');
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        if (organizationId != null && !organizationId.equals("null"))
            this.organizationId = organizationId;
        this.roleId = role;
    }

    public TNMUser(String id, String username, String email, String encryptedPassword, String organizationId, Role role) {
        super.id = id;
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        if (organizationId != null && !organizationId.equals("null"))
            this.organizationId = organizationId;
        this.roleId = role.getId();
    }

    public TNMUser(String username, String email, String encryptedPassword, String organizationId, Role role) {
        super.id = generateID('A');
        this.username = username;
        this.email = email;
        this.encryptedPassword = encryptedPassword;
        if (organizationId != null && !organizationId.equals("null"))
            this.organizationId = organizationId;
        this.roleId = role.getId();
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Integer getRoleId() {
        return roleId;
    }

//    private TNMOrganization getOrganization() {
//
//        return TNMOrganizationDAO.find(organizationId);
//    }
//
//    public String getOrganizationDisplayName() {
//        TNMOrganization org = getOrganization();
//        return (org == null) ? "" : org.getName();
//    }

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
        switch (this.role()) {
            case ADMIN:
                if (this.getOrganizationId() == null) {
                    return "Admin";
                } else {
                    return "Manager";
                }
            case USER:
                return "User";
            default:
                return null;
        }
    }
}
