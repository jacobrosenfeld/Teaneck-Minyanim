package com.tbdev.teaneckminyanim.admin.structure.settings;

import com.tbdev.teaneckminyanim.admin.structure.TNMSaveable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.*;

@Repository
@Transactional
public class TNMSettingsDAO extends JdbcDaoSupport implements TNMSaveable<TNMSettings> {

    @Autowired
    public TNMSettingsDAO(DataSource dataSource) {
        this.setDataSource(dataSource);
    }

    public TNMSettings findByName(String setting) {
        String sql = TNMSettingsMapper.BASE_SQL + " WHERE SETTING = ? ";

        Object[] params = new Object[] { setting };
        TNMSettingsMapper mapper = new TNMSettingsMapper();

        try {
            TNMSettings settingsSet = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return settingsSet;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public TNMSettings findById(String id) {
        String sql = TNMSettingsMapper.BASE_SQL + " WHERE ID = ? ";

        Object[] params = new Object[] { id };
        TNMSettingsMapper mapper = new TNMSettingsMapper();

        try {
            TNMSettings settingsSet = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return settingsSet;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<TNMSettings> getAll() {
        String sql = "SELECT SETTING, ENABLED, ID, TEXT, TYPE FROM SETTINGS";
        
        TNMSettingsMapper mapper = new TNMSettingsMapper();

        List<Map<String, Object>> settingsMaps = this.getJdbcTemplate().queryForList(sql);

        List<TNMSettings> settings = new ArrayList<TNMSettings>();

//        iterate through the list and create an TNMSettings object for each row
        for (Map<String, Object> settingsMap : settingsMaps) {
            settings.add(mapper.mapRow(settingsMap));
        }

        return settings;
    }

    @Override
    public boolean save(TNMSettings setting) {
        String sql = String.format("INSERT INTO SETTINGS VALUES ('%s', '%d')", setting.getSetting(), setting.getEnabled(), setting.getID(), setting.getText(), setting.getType());

        try {
            this.getConnection().createStatement().executeUpdate(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(TNMSettings objectToDelete) {
        String sql = String.format("DELETE FROM SETTINGS WHERE ID='%s'", objectToDelete.getId());

        try {
            this.getConnection().createStatement().executeUpdate(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean update(TNMSettings settingtoUpdate) {
        try {
            String sql = "UPDATE SETTINGS SET ENABLED=%b, TEXT='%s' WHERE ID='%s';";


            getConnection().createStatement().executeUpdate(String.format(sql, settingtoUpdate.getEnabled(), settingtoUpdate.getText(), settingtoUpdate.getId(), settingtoUpdate.getType()));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}