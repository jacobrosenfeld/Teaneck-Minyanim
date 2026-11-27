package com.tbdev.teaneckminyanim.service;

import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.AuthAuditEventRepository;
import com.tbdev.teaneckminyanim.repo.MagicLinkTokenRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.auth.AuthAuditService;
import com.tbdev.teaneckminyanim.service.auth.PasskeyCredentialService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Disabling or deleting an account revokes or removes its app sessions and API keys, on every path. */
class TNMUserServiceTokenRevocationTest {
    private final TNMUserRepository repository = mock(TNMUserRepository.class);
    private final AppTokenService appTokens = mock(AppTokenService.class);
    private final ApiKeyService apiKeys = mock(ApiKeyService.class);
    private final AuthAuditService audit = mock(AuthAuditService.class);
    private final TNMUserService users = new TNMUserService(repository, provider(appTokens), provider(apiKeys), provider(audit));

    private TNMUser superAdmin;
    private TNMUser target;

    @BeforeEach
    void setUp() {
        superAdmin = user("SUPER", "superadmin", null);
        target = user("TARGET", "targetuser", "ORGA");
        when(repository.findById("SUPER")).thenReturn(Optional.of(superAdmin));
        when(repository.findById("TARGET")).thenReturn(Optional.of(target));
        when(repository.findByUsername("superadmin")).thenReturn(Optional.of(superAdmin));
        when(repository.findAll()).thenReturn(List.of(superAdmin, target));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingADisabledUserRevokesSessionsAndKeysAndAudits() {
        when(appTokens.revokeAllForAccount("TARGET", "USER_DISABLED")).thenReturn(2);
        when(apiKeys.revokeAllForAccount("TARGET", "USER_DISABLED")).thenReturn(1);
        target.setEnabled(false);

        assertTrue(users.update(target));

        verify(appTokens).revokeAllForAccount("TARGET", "USER_DISABLED");
        verify(apiKeys).revokeAllForAccount("TARGET", "USER_DISABLED");
        verify(audit).record(eq("TOKENS_REVOKED_USER_DISABLED"), eq("TARGET"), any(), eq("sessions=2 keys=1"));
    }

    @Test
    void savingAnEnabledUserRevokesNothing() {
        assertTrue(users.update(target));
        verifyNoInteractions(appTokens, apiKeys, audit);
    }

    @Test
    void nothingToRevokeIsNotAudited() {
        target.setEnabled(false);
        assertTrue(users.update(target));
        verify(appTokens).revokeAllForAccount("TARGET", "USER_DISABLED");
        verify(audit, never()).record(eq("TOKENS_REVOKED_USER_DISABLED"), any(), any(), any());
    }

    @Test
    void revocationFailureDoesNotUndoTheDisable() {
        when(appTokens.revokeAllForAccount(anyString(), anyString())).thenThrow(new IllegalStateException("db down"));
        target.setEnabled(false);
        assertTrue(users.update(target));
        verify(repository).save(target);
    }

    @Test
    void deletingAUserRemovesSessionsAndKeysFirst() {
        assertTrue(users.delete(target));
        InOrder order = inOrder(appTokens, apiKeys, repository);
        order.verify(appTokens).deleteAllForAccount("TARGET");
        order.verify(apiKeys).deleteAllForAccount("TARGET");
        order.verify(repository).delete(target);
    }

    @Test
    void userManagementDisableRevokesSessionsAndKeys() {
        when(appTokens.revokeAllForAccount("TARGET", "USER_DISABLED")).thenReturn(1);
        UserManagementService management = userManagement();

        management.setEnabled("TARGET", false);

        assertFalse(target.isEnabled());
        verify(appTokens).revokeAllForAccount("TARGET", "USER_DISABLED");
        verify(apiKeys).revokeAllForAccount("TARGET", "USER_DISABLED");
        verify(audit).record(eq("TOKENS_REVOKED_USER_DISABLED"), eq("TARGET"), any(), eq("sessions=1 keys=0"));
    }

    @Test
    void userManagementEnableRevokesNothing() {
        target.setEnabled(false);
        userManagement().setEnabled("TARGET", true);
        verifyNoInteractions(appTokens, apiKeys);
    }

    @Test
    void userManagementDeleteRemovesSessionsAndKeysBeforeTheAccount() {
        target.setEnabled(false);
        UserManagementService management = userManagement();

        management.delete("TARGET");

        InOrder order = inOrder(appTokens, apiKeys, repository);
        order.verify(appTokens).deleteAllForAccount("TARGET");
        order.verify(apiKeys).deleteAllForAccount("TARGET");
        order.verify(repository).delete(target);
    }

    private UserManagementService userManagement() {
        OrganizationService organizations = mock(OrganizationService.class);
        when(organizations.findById(any())).thenReturn(Optional.of(new Organization()));
        return new UserManagementService(users, repository, organizations, mock(PasskeyCredentialService.class),
                mock(MagicLinkTokenRepository.class), mock(AuthAuditEventRepository.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    private static TNMUser user(String id, String username, String organizationId) {
        return TNMUser.builder().id(id).username(username).email(username + "@example.com")
                .encryptedPassword("!PASSWORDLESS").organizationId(organizationId).roleId(Role.ADMIN.getId())
                .enabled(true).build();
    }
}
