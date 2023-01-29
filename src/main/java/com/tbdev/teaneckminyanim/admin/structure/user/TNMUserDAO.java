package com.tbdev.teaneckminyanim.admin.structure.user;

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
public class TNMUserDAO extends JdbcDaoSupport implements TNMSaveable<TNMUser> {

    @Autowired
    public TNMUserDAO(DataSource dataSource) {
        this.setDataSource(dataSource);
    }

    public TNMUser findByName(String username) {
        String sql = TNMUserMapper.BASE_SQL + " WHERE u.USERNAME = ? ";

        Object[] params = new Object[] { username };
        TNMUserMapper mapper = new TNMUserMapper();

        try {
            TNMUser userInfo = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return userInfo;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public TNMUser findById(String id) {
        String sql = TNMUserMapper.BASE_SQL + " WHERE u.ID = ? ";

        Object[] params = new Object[] { id };
        TNMUserMapper mapper = new TNMUserMapper();

        try {
            TNMUser userInfo = this.getJdbcTemplate().queryForObject(sql, params, mapper);
            return userInfo;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<TNMUser> getAll() {
        String sql = "SELECT ID, USERNAME, EMAIL, ENCRYPTED_PASSWORD, ROLE_ID, ORGANIZATION_ID FROM ACCOUNT";

        TNMUserMapper mapper = new TNMUserMapper();

        List<Map<String, Object>> userMaps = this.getJdbcTemplate().queryForList(sql);

        List<TNMUser> users = new ArrayList<TNMUser>();

//        iterate through the list and create an TNMUser object for each row
        for (Map<String, Object> userMap : userMaps) {
            users.add(mapper.mapRow(userMap));
        }





//        System.out.println(users);
        /*
        try {
//            Class.forName("org.h2.Driver");
//            String url = "jdbc:h2:file:./data/demo";
//            Connection con = DriverManager.getConnection(url);
            Statement stmt = this.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                System.out.println("rs = " + rs.getString("NAME"));
            }
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        */


//        List<Map<String, Object>> users = this.getJdbcTemplate().queryForList(sql, mapper);

//        mapper.mapRow()

//        System.out.println("Users: " + users);

        return users;
    }

    @Override
    public boolean save(TNMUser user) {
        String sql = String.format("INSERT INTO ACCOUNT VALUES ('%s', '%s', '%s', '%s', '%s', '%d')", user.getId(), user.getUsername(), user.getEmail(), user.getEncryptedPassword(), user.getOrganizationId(), user.getRoleId());

        try {
            this.getConnection().createStatement().executeUpdate(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(TNMUser objectToDelete) {
        String sql = String.format("DELETE FROM ACCOUNT WHERE ID='%s'", objectToDelete.getId());

        try {
            this.getConnection().createStatement().executeUpdate(sql);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean update(TNMUser objectToUpdate) {
        try {
            String sql = "UPDATE ACCOUNT SET USERNAME='%s', EMAIL='%s', ENCRYPTED_PASSWORD='%s', ORGANIZATION_ID='%s', ROLE_ID=%d WHERE ID='%s'";

//            String sql = "UPDATE ACCOUNT SET USERNAME=?, EMAIL=?, ORGANIZATION_ID=?, ROLE_ID=? WHERE ID=?;";
//            getJdbcTemplate().query(sql, new PreparedStatementSetter() {
//                        public void setValues(PreparedStatement preparedStatement) throws SQLException {
//                            preparedStatement.setString(0, objectToUpdate.getUsername());
//                            preparedStatement.setString(1, objectToUpdate.getEmail());
//                            preparedStatement.setString(2, objectToUpdate.getOrganizationId());
//                            preparedStatement.setInt(3, objectToUpdate.getRoleId().intValue());
//                            preparedStatement.setString(4, objectToUpdate.getId());
//                        }
//                    }, new TNMUserMapper());


            getConnection().createStatement().executeUpdate(String.format(sql, objectToUpdate.getUsername(), objectToUpdate.getEmail(), objectToUpdate.getEncryptedPassword(), objectToUpdate.getOrganizationId(), objectToUpdate.getRoleId(), objectToUpdate.getId()));

//            String sql = "UPDATE ACCOUNT SET USERNAME=?, EMAIL=?, ORGANIZATION_ID=?, ROLE_ID=? WHERE ID=?;";
//            PreparedStatement stmt = this.getConnection().prepareStatement(sql);
//            stmt.setString(0, objectToUpdate.getUsername());
//            stmt.setString(1, objectToUpdate.getEmail());
//            stmt.setString(2, objectToUpdate.getOrganizationId());
//            stmt.setInt(3, objectToUpdate.getRoleId().intValue());
//            stmt.setString(4, objectToUpdate.getId());
//            stmt.executeUpdate();
//            getConnection().commit();
//            System.out.println("Successfully updated TNMUser");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}