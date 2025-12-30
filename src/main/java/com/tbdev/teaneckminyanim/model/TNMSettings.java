package com.tbdev.teaneckminyanim.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "NOTIFICATIONS")
public class TNMSettings {

    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;

    @Column(name = "SETTING", nullable = false)
    private String setting;

    @Column(name = "ENABLED", nullable = true)
    private String enabled;

    @Column(name = "TEXT", nullable = true)
    private String text;

    @Column(name = "TYPE", nullable = false)
    private String type;

    @Column(name = "EXPIRATION_DATE", nullable = true)
    private String expirationDate;

    @Column(name = "MAX_DISPLAYS", nullable = true)
    private Integer maxDisplays;

    @Column(name = "VERSION", nullable = true)
    private Long version;

}
