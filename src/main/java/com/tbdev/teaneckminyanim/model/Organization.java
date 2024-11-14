package com.tbdev.teaneckminyanim.model;


import com.tbdev.teaneckminyanim.enums.Nusach;
import lombok.*;

import javax.persistence.*;
import java.net.URI;

@Entity
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Table(name = "organization")
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

}
