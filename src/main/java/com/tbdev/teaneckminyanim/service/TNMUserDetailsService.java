package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.model.TNMUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@Service
public class TNMUserDetailsService implements UserDetailsService {

    @Autowired
    private TNMUserService TNMUserDAO;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        final String ip = getClientIP();
        if (loginAttemptService.isBlocked(ip)) {
            throw new RuntimeException("blocked");
        }
        TNMUser user = this.TNMUserDAO.findByIdentifier(userName);

        if (user == null) {
            log.debug("Login lookup found no account for the supplied identifier");
            throw new UsernameNotFoundException("User " + userName + " was not found in the database");
        }
        if (user.getRoleId() == null || user.role() == null) {
            // An account without a valid role must never authenticate (and would otherwise NPE).
            log.warn("Rejecting login for account {} because it has no valid role", user.getUsername());
            throw new UsernameNotFoundException("User " + userName + " has no valid role");
        }

        // Do not log the TNMUser itself: its toString() includes the password hash.
        log.debug("Loaded account {} for authentication", user.getUsername());

        List<GrantedAuthority> grantList = List.of(new SimpleGrantedAuthority(user.role().getName()));

        // Disabled accounts are returned with enabled=false so password login rejects them with
        // DisabledException. Passkey and magic-link logins reject disabled accounts before this point,
        // and AccountAccessFilter ends any existing (including database-persisted) session on the
        // next request once an account is disabled.
        return new User(
                user.getUsername(),
                user.getEncryptedPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                grantList);
    }

    private String getClientIP() {
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
