package com.tbdev.teaneckminyanim.structure.model;


import com.tbdev.teaneckminyanim.structure.global.Nusach;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.net.URI;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ORGANIZATION")
public class Organization {

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "ADDRESS", nullable = true)
    private String address;
    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;

    @Column(name = "COLOR", nullable = false)
    private String orgColor;
    @Column(name = "SITE_URI", nullable = true)
    private String websiteURIStr;

    @Column(name = "NUSACH", nullable = false)
    private String nusachStr;

    @Column(name = "CALENDAR", nullable = false)
    private String calendar;

    @Transient
    private Nusach nusach;

    @Transient
    private URI websiteURI;

    public Organization(String id, String name, String address, URI websiteURI, String orgColor) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.websiteURI = websiteURI;
        this.orgColor = orgColor;
    }
    public Organization(String id, String name, String address, URI websiteURI, Nusach nusach, String orgColor) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.websiteURI = websiteURI;
        this.orgColor = orgColor;
    }

    public Organization(String name, String address, URI websiteURI, Nusach nusach, String orgColor) {
//        super.id = generateID('O');
        this.name = name;
        this.address = address;
        this.websiteURI = websiteURI;
        this.nusach = nusach;
        this.orgColor = orgColor;
    }
}
