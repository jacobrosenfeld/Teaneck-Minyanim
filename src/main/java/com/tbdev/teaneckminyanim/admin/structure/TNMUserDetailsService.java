package com.tbdev.teaneckminyanim.admin.structure;

import com.tbdev.teaneckminyanim.admin.structure.user.TNMUser;
import com.tbdev.teaneckminyanim.admin.structure.user.TNMUserDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TNMUserDetailsService implements UserDetailsService {

    @Autowired
    private TNMUserDAO TNMUserDAO;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        TNMUser user = this.TNMUserDAO.findByName(userName);

        if (user == null) {
            System.out.println("User not found! " + userName);
            throw new UsernameNotFoundException("User " + userName + " was not found in the database");
        }

        System.out.println("Found User: " + user);

//        List<String> roleNames = this.appRoleDAO.getRoleNames(user.getId());
//
        List<GrantedAuthority> grantList = new ArrayList<GrantedAuthority>();
//        if (roleNames != null) {
//            for (String role : roleNames) {
                // ROLE_USER, ROLE_ADMIN,..

                GrantedAuthority authority = new SimpleGrantedAuthority(user.role().getName());
                grantList.add(authority);
//            }
//        }

        UserDetails userDetails = (UserDetails) new User(user.getUsername(), user.getEncryptedPassword(), grantList);

        return userDetails;
    }

}