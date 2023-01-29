package com.tbdev.teaneckminyanim.admin.structure.organization;

import com.tbdev.teaneckminyanim.global.Nusach;
import org.springframework.jdbc.core.RowMapper;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import javax.swing.text.AttributeSet.ColorAttribute;

public class OrganizationMapper implements RowMapper<Organization>, Serializable {

    public static final String BASE_SQL = "SELECT u.COLOR, u.ID, u.NAME, u.ADDRESS, u.SITE_URI, u.NUSACH FROM ORGANIZATION u ";

    @Override
    public Organization mapRow(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("ID");
        String name = rs.getString("NAME");
        String address = rs.getString("ADDRESS");
        String nusach = rs.getString("NUSACH");
        String orgColor = rs.getString("COLOR");

        URI siteURI = null;
        try {
            String siteURIString = rs.getString("SITE_URI");
            if (siteURIString != null && !siteURIString.isEmpty()) {
                siteURI = new URI(siteURIString);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return new Organization(id, name, address, siteURI, Nusach.fromString(nusach), orgColor);
    }

    public Organization mapRow(Map<String, Object> m) {
        String id = (String) m.get("ID");
        String name = (String) m.get("NAME");
        String address = (String) m.get("ADDRESS");
        String nusach = (String) m.get("NUSACH");
        String orgColor = (String) m.get("COLOR");

        URI siteURI = null;
        try {
            String siteURIString = (String) m.get("SITE_URI");
            if (siteURIString != null && !siteURIString.isEmpty()) {
                siteURI = new URI(siteURIString);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return new Organization(id, name, address, siteURI, Nusach.fromString(nusach), orgColor);
    }
}