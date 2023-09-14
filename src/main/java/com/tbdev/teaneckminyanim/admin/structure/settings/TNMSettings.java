package com.tbdev.teaneckminyanim.admin.structure.settings;

import javax.persistence.*;

import com.tbdev.teaneckminyanim.admin.structure.IDGenerator;
import com.tbdev.teaneckminyanim.admin.structure.TNMObject;

@Table(name="SETTINGS")
public class TNMSettings extends TNMObject implements IDGenerator {
    @Column(name="Setting", nullable = false, unique = true)
    private String setting;

    @Column(name="ENABLED", nullable = true)
    private boolean enable;

    @Column(name="ID")
    private String id;

    @Column(name="TEXT", nullable = true)
    private String text;

    public TNMSettings(String setting, boolean enable, String id, String text) {
        super.id = id;
        this.setting = setting;
        this.enable = enable;
        this.text = text;
    }

    public TNMSettings(String setting, boolean enable, String text) {
        super.id = generateID('S');
        this.setting = setting;
        this.enable = enable;
        this.text = text;
    }

    public String getSetting() {
        return setting;
    }

    public boolean getEnabled() {
        return enable;
    }

    public String getID() {
        return id;
    }

    public String getText() {
        return text;
    }
}
