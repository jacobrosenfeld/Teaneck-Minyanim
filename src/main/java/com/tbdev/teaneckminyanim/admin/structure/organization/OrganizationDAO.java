package com.tbdev.teaneckminyanim.admin.structure.organization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.tbdev.teaneckminyanim.admin.structure.TNMSaveable;
import com.tbdev.teaneckminyanim.admin.structure.user.TNMUser;
import com.tbdev.teaneckminyanim.admin.structure.user.TNMUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class OrganizationDAO extends JdbcDaoSupport implements TNMSaveable<Organization> {

    @Autowired
    public OrganizationDAO(DataSource dataSource) {
        this.setDataSource(dataSource);
    }

    public Organization findByName(String name) {
        String sql = OrganizationMapper.BASE_SQL + " WHERE u.NAME = ? ";

        Object[] params = new Object[] { name };
        OrganizationMapper mapper = new OrganizationMapper();

        try {
            Organization orgInfo = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return orgInfo;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public Organization findById(String id) {
        String sql = OrganizationMapper.BASE_SQL + " WHERE u.ID = ? ";

        Object[] params = new Object[] { id };
        OrganizationMapper mapper = new OrganizationMapper();

        try {
            Organization orgInfo = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return orgInfo;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<Organization> getAll() {
        String sql = "SELECT * FROM ORGANIZATION";

        OrganizationMapper mapper = new OrganizationMapper();

        List<Map<String, Object>> orgMaps = this.getJdbcTemplate().queryForList(sql);

        List<Organization> organizations = new ArrayList<>();

        for (Map<String, Object> orgMap : orgMaps) {
            organizations.add(mapper.mapRow(orgMap));
        }

        return organizations;
    }

    @Override
    public boolean save(Organization organization) {
        String sql;
        if (organization.getWebsiteURI() != null) {
            sql = String.format("INSERT INTO ORGANIZATION (ID, NAME, ADDRESS, SITE_URI, NUSACH, COLOR) VALUES ('%s', '%s', '%s', '%s', '%s', '%s')", organization.getId(), organization.getName(), organization.getAddress(), organization.getWebsiteURI(), organization.getNusach().getText(), organization.getOrgColor());
        } else {
            sql = String.format("INSERT INTO ORGANIZATION (ID, NAME, ADDRESS, SITE_URI, NUSACH, COLOR) VALUES ('%s', '%s', '%s', NULL, '%s', '%s')", organization.getId(), organization.getName(), organization.getAddress(), organization.getNusach().getText(), organization.getOrgColor());
        }

        try {
            this.getConnection().createStatement().execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(Organization objectToDelete) {
        String sql = String.format("DELETE FROM ORGANIZATION WHERE ID='%s'", objectToDelete.getId());

        try {
            this.getConnection().createStatement().execute(sql);

            String matchingOrgsSQL = String.format("DELETE FROM ACCOUNT WHERE ORGANIZATION_ID='%s'", objectToDelete.getId());

            try {
                this.getConnection().createStatement().execute(matchingOrgsSQL);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean update(Organization organizationToUpdate) {
        String sql;
        if (organizationToUpdate.getWebsiteURI() != null) {
            sql = String.format("UPDATE ORGANIZATION SET NAME='%s', ADDRESS='%s', SITE_URI='%s', NUSACH='%s', COLOR='%s' WHERE ID='%s'", organizationToUpdate.getName(), organizationToUpdate.getAddress(), organizationToUpdate.getWebsiteURI(), organizationToUpdate.getNusach().getText(), organizationToUpdate.getOrgColor(), organizationToUpdate.getId());
        } else {
            sql = String.format("UPDATE ORGANIZATION SET NAME='%s', ADDRESS='%s', SITE_URI=NULL, NUSACH='%s', COLOR='%s' WHERE ID='%s'", organizationToUpdate.getName(), organizationToUpdate.getAddress(), organizationToUpdate.getNusach().getText(), organizationToUpdate.getOrgColor(), organizationToUpdate.getId());
        }

        try {
            this.getConnection().createStatement().execute(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<TNMUser> getUsersForOrganization(Organization organization) {
        String sql = String.format("SELECT * FROM ACCOUNT WHERE ORGANIZATION_ID='%s'", organization.getId());

        TNMUserMapper mapper = new TNMUserMapper();

        List<Map<String, Object>> userMaps = this.getJdbcTemplate().queryForList(sql);

        List<TNMUser> users = new ArrayList<>();

//        iterate through the list and create an TNMUser object for each row
        for (Map<String, Object> userMap : userMaps) {
            users.add(mapper.mapRow(userMap));
        }

        return users;
    }
}