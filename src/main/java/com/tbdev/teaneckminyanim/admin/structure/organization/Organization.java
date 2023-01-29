package com.tbdev.teaneckminyanim.admin.structure.organization;

import com.tbdev.teaneckminyanim.admin.structure.TNMObject;
import com.tbdev.teaneckminyanim.admin.structure.IDGenerator;
import com.tbdev.teaneckminyanim.global.Nusach;

import javax.persistence.Column;
import javax.persistence.Table;

import java.net.URI;

@Table(name="ORGANIZATION")
public class Organization extends TNMObject implements IDGenerator {
    @Column(name="NAME", nullable = false)
    private String name;

    @Column(name="ADDRESS", nullable = true)
    private String address;

    @Column(name="SITE_URI", nullable = true)
    private URI websiteURI;

    @Column(name="NUSACH", nullable = false)
    private Nusach nusach;

    @Column(name="COLOR", nullable = false)
    private String orgColor;

    public Organization(String id, String username, String address, URI websiteURI, Nusach nusach, String orgColor) {
        super.id = id;
        this.name = username;
        this.address = address;
        this.websiteURI = websiteURI;
        this.nusach = nusach;
        this.orgColor = orgColor;
    }

    public Organization(String username, String address, URI websiteURI, Nusach nusach, String orgColor) {
        super.id = generateID('O');
        this.name = username;
        this.address = address;
        this.websiteURI = websiteURI;
        this.nusach = nusach;
        this.orgColor = orgColor;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public URI getWebsiteURI() {
        return websiteURI;
    }

    public Nusach getNusach() {
        return nusach;
    }
    
    public String getOrgColor() {
        return orgColor;
    }
}
