package com.tbdev.teaneckminyanim.structure.model;

import javax.persistence.Column;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@NoArgsConstructor
@Table(name = "LOCATION")
@Getter
public class Location {

    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;
    @Column(name="NAME")
    private String name;
    @Column(name="ORGANIZATION_ID")
    private String organizationId;

    public Location(String id, String name, String organizationId) {
        this.id = id;
        this.name = name;
        this.organizationId = organizationId;
    }

    public Location(String name, String organizationId) {
        this.name = name;
        this.organizationId = organizationId;
    }
}
