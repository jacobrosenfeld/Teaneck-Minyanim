package com.tbdev.teaneckminyanim.admin.structure.settings;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;

public class TNMSettingsMapper implements RowMapper<TNMSettings>, Serializable {

    public static final String BASE_SQL = "SELECT u.SETTING, u.ENABLED, u.SETTING_ID FROM SETTINGS u ";

    @Override
    public TNMSettings mapRow(ResultSet rs, int rowNum) throws SQLException {

        String setting = rs.getString("SETTING");
        Boolean enabled = rs.getBoolean("ENABLED");
        String id = rs.getString("SETTINGS_ID");

        return new TNMSettings(setting, enabled, id);
    }

    public TNMSettings mapRow(Map<String, Object> m) {

        String setting = (String) m.get("SETTING");
        Boolean enabled = Boolean.valueOf(m.get("ENABLED").toString());
        String id = (String) m.get("SETTING_ID");


        return new TNMSettings(setting, enabled, id);
    }

}