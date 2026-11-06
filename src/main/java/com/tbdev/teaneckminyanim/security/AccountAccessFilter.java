package com.tbdev.teaneckminyanim.security;

import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reject stale sessions after disabling, deleting or changing an account's role. */
public class AccountAccessFilter extends OncePerRequestFilter {
    private final TNMUserService users;

    public AccountAccessFilter(TNMUserService users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            TNMUser user = users.findByName(auth.getName());
            if (user == null || user.isDisabled() || user.getRoleId() == null || user.role() == null
                    || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(user.role().getName()))) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/admin/login");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
