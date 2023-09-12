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

    @Column(name="SETTING_ID")
    private String settingID;

    public TNMSettings(String setting, boolean enable, String settingID) {
        super.id = id;
        this.setting = setting;
        this.enable = enable;
    }

    public TNMSettings(String setting, boolean enable) {
        super.id = generateID('S');
        this.setting = setting;
        this.enable = enable;
    }

    public String getSetting() {
        return setting;
    }

    public boolean getEnabled() {
        return enable;
    }

    public String getSettingID() {
        return settingID;
    }
}
