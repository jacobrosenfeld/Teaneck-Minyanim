package com.tbdev.teaneckminyanim.admin.structure;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public abstract class TNMObject {
    @Id
    @Column(name="ID", nullable = false, unique = true)
    protected String id;

    public TNMObject() {
    }

    public String getId() {
        return id;
    }
}
