package com.tbdev.teaneckminyanim.model;

import javax.persistence.Column;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Table(name = "LOCATION")
public class Location {

    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;
    @Column(name="NAME")
    private String name;
    @Column(name="ORGANIZATION_ID")
    private String organizationId;

    public Location(String name, String organizationId) {
        this.name = name;
        this.organizationId = organizationId;
    }
}
