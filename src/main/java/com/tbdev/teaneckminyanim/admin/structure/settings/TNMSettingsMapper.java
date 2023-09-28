package com.tbdev.teaneckminyanim.admin.structure.settings;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;

public class TNMSettingsMapper implements RowMapper<TNMSettings>, Serializable {

    public static final String BASE_SQL = "SELECT SETTING, ENABLED, ID, TEXT FROM SETTINGS ";

    @Override
    public TNMSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        String setting = rs.getString("SETTING");
        Boolean enabled = rs.getBoolean("ENABLED"); // Use Boolean, not boolean
        String id = rs.getString("ID");
        String text = rs.getString("TEXT");
        String type = rs.getString("TYPE");
    
        String enabledAsString = (enabled != null) ? enabled.toString() : "null";
    
        return new TNMSettings(setting, enabledAsString, id, text, type);
    }
    
    

    public TNMSettings mapRow(Map<String, Object> m) {

        String setting = (String) m.get("SETTING");
        Boolean enabled = m.get("ENABLED") != null ? Boolean.valueOf(m.get("ENABLED").toString()) : null;
        String enabledAsString = enabled != null ? enabled.toString() : "null";
        String id = (String) m.get("ID");
        String text = (String) m.get("TEXT");
        String type = (String) m.get("TYPE");

        return new TNMSettings(setting, enabledAsString, id, text, type);
    }

}