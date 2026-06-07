package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.TNMUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TNMUserDetailsServiceTest {

    private TNMUserDetailsService userDetailsService;
    private TNMUserService userService;
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        userDetailsService = new TNMUserDetailsService();
        userService = mock(TNMUserService.class);
        loginAttemptService = mock(LoginAttemptService.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        ReflectionTestUtils.setField(userDetailsService, "TNMUserDAO", userService);
        ReflectionTestUtils.setField(userDetailsService, "loginAttemptService", loginAttemptService);
        ReflectionTestUtils.setField(userDetailsService, "request", request);
    }

    @Test
    void loadUserByUsernameMarksDisabledAccountsDisabledForSpringSecurity() {
        when(loginAttemptService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userService.findByName("manager")).thenReturn(user(false));

        UserDetails userDetails = userDetailsService.loadUserByUsername("manager");

        assertFalse(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(authority -> Role.ADMIN.getName().equals(authority.getAuthority())));
    }

    @Test
    void loadUserByUsernameKeepsEnabledAccountsEnabledForSpringSecurity() {
        when(loginAttemptService.isBlocked("127.0.0.1")).thenReturn(false);
        when(userService.findByName("manager")).thenReturn(user(true));

        UserDetails userDetails = userDetailsService.loadUserByUsername("manager");

        assertTrue(userDetails.isEnabled());
    }

    private TNMUser user(boolean enabled) {
        return TNMUser.builder()
                .id("A1")
                .username("manager")
                .email("manager@example.com")
                .encryptedPassword("encrypted")
                .organizationId("org-a")
                .roleId(Role.ADMIN.getId())
                .enabled(enabled)
                .build();
    }
}
