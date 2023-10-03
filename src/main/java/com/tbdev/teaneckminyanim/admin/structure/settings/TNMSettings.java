package com.tbdev.teaneckminyanim.admin.structure.settings;

import javax.persistence.*;

import com.tbdev.teaneckminyanim.admin.structure.IDGenerator;
import com.tbdev.teaneckminyanim.admin.structure.TNMObject;

@Table(name="SETTINGS")
public class TNMSettings extends TNMObject implements IDGenerator {
    @Column(name="Setting", nullable = false)
    private String setting;

    @Column(name="ENABLED", nullable = true)
    private String enabled;

    @Column(name="ID", nullable = false, unique = true)
    private String id;

    @Column(name="TEXT", nullable = true)
    private String text;

    @Column(name="TYPE", nullable = false)
    private String type;

    public TNMSettings(String setting, String enabled, String id, String text, String type) {
        super.id = id;
        this.setting = setting;
        this.enabled = enabled;
        this.text = text;
        this.type = type;
    }

    public TNMSettings(String setting, String enable, String text, String type) {
        super.id = generateID('S');
        this.setting = setting;
        this.enabled = enabled;
        this.text = text;
        this.type = type;
    }

    public String getSetting() {
        return setting;
    }

    public String getEnabled() {
        return enabled;
    }

    public String getID() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getType() {
        return type;
    }

}
