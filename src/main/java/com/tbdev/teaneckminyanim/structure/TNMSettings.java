package com.tbdev.teaneckminyanim.structure;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "SETTINGS")
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

    public TNMSettings(String setting, String enabled, String id, String text, String type) {
        this.id = id;
        this.setting = setting;
        this.enabled = enabled;
        this.text = text;
        this.type = type;
    }

    public TNMSettings(String setting, String enabled, String text, String type) {
//        super.id = generateID('S');
        this.setting = setting;
        this.enabled = enabled;
        this.text = text;
        this.type = type;
    }
}
