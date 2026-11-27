package com.tbdev.teaneckminyanim.controllers;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.ApplicationSettingsService;
import com.tbdev.teaneckminyanim.service.OrganizationService;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Super-admin-only page for personal API keys and mobile app sessions ({@code /admin/api-keys}).
 *
 * <p>A newly created key is shown exactly once, in the response to the create POST. That response is
 * rendered directly (no redirect), is {@code Cache-Control: no-store}, and the raw key is never put in the
 * session, flash attributes or any later model.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ApiKeyAdminController {

    static final String PAGE_PATH = "/admin/api-keys";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);

    private final ApiKeyService apiKeyService;
    private final AppTokenService appTokenService;
    private final TNMUserService userService;
    private final OrganizationService organizationService;
    private final ApplicationSettingsService settingsService;

    @ModelAttribute("siteName")
    public String siteName() {
        return settingsService.getSiteName();
    }

    @ModelAttribute("supportEmail")
    public String supportEmail() {
        return settingsService.getSupportEmail();
    }

    /** A key as shown in the table. */
    public record KeyRow(String id, String name, String owner, String displayPrefix, boolean sameAsOwner,
                         List<String> scopes, List<String> shuls, String createdAt, String lastUsedAt,
                         boolean active, String revokedAt) {}

    /** A mobile app session as shown in the table. */
    public record SessionRow(String id, String user, String deviceName, String method, String createdAt,
                             String lastUsedAt, String refreshExpiresAt) {}

    /** An account that can own a key. {@code shulIds} is what the owner can access, for limiting the shul list. */
    public record OwnerOption(String id, String username, String role, boolean superAdmin, String shulIds) {}

    /** Submitted create-form values, kept so a validation error doesn't lose them. Never holds a key. */
    public record CreateForm(String ownerId, String name, boolean specificScopes, Set<String> scopes,
                             Set<String> shuls) {
        static CreateForm empty() {
            return new CreateForm("", "", false, Set.of(), Set.of());
        }
    }

    // -----------------------------------------------------------------------
    // View
    // -----------------------------------------------------------------------

    @GetMapping(PAGE_PATH)
    public ModelAndView page() {
        requireSuperAdmin();
        return buildPage(CreateForm.empty(), null);
    }

    // -----------------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------------

    /**
     * Creates a key and renders the page with the key shown once. Deliberately not a redirect: the raw key
     * lives only in this response.
     */
    @AdminAction("api-key.create")
    @PostMapping(PAGE_PATH + "/create")
    public ModelAndView create(@RequestParam(name = "ownerId", required = false) String ownerId,
                               @RequestParam(name = "name", required = false) String name,
                               @RequestParam(name = "permissions", required = false, defaultValue = "same") String permissions,
                               @RequestParam(name = "scopes", required = false) List<String> scopeValues,
                               @RequestParam(name = "shuls", required = false) List<String> shulIds,
                               HttpServletResponse response) {
        requireSuperAdmin();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        boolean specific = "scopes".equals(permissions);
        Set<String> scopeSet = scopeValues == null ? Set.of() : new LinkedHashSet<>(scopeValues);
        Set<String> shulSet = shulIds == null ? Set.of() : new LinkedHashSet<>(shulIds);
        CreateForm form = new CreateForm(ownerId == null ? "" : ownerId, name == null ? "" : name, specific,
                scopeSet, shulSet);
        try {
            Set<ApiScope> scopes = EnumSet.noneOf(ApiScope.class);
            if (specific) {
                for (String value : scopeSet) {
                    scopes.add(ApiScope.fromValue(value));
                }
            }
            ApiKeyService.CreatedApiKey created =
                    apiKeyService.create(ownerId, name, !specific, scopes, shulSet);
            ModelAndView mv = buildPage(CreateForm.empty(), null);
            mv.addObject("createdKeyName", created.key().getName());
            mv.addObject("createdKey", created.rawKey());
            return mv;
        } catch (IllegalArgumentException e) {
            ModelAndView mv = buildPage(form, e.getMessage());
            mv.addObject("openCreateModal", true);
            return mv;
        }
    }

    @AdminAction("api-key.revoke")
    @PostMapping(PAGE_PATH + "/{id}/revoke")
    public RedirectView revoke(@PathVariable String id, RedirectAttributes redirectAttributes) {
        requireSuperAdmin();
        try {
            ApiKey key = apiKeyService.revoke(id);
            redirectAttributes.addFlashAttribute("successMessage", "Revoked API key \"" + key.getName() + "\".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return new RedirectView(PAGE_PATH, true);
    }

    @AdminAction("app-session.revoke")
    @PostMapping(PAGE_PATH + "/sessions/{id}/revoke")
    public RedirectView revokeSession(@PathVariable String id, RedirectAttributes redirectAttributes) {
        requireSuperAdmin();
        try {
            appTokenService.revokeSession(id);
            redirectAttributes.addFlashAttribute("successMessage", "Revoked the mobile session.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return new RedirectView(PAGE_PATH, true);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ModelAndView buildPage(CreateForm form, String createError) {
        TNMUser current = userService.getCurrentUser();
        List<TNMUser> accounts = userService.getAll();
        Map<String, TNMUser> accountsById = accounts.stream()
                .collect(Collectors.toMap(TNMUser::getId, Function.identity(), (a, b) -> a));
        List<Organization> shuls = new ArrayList<>(organizationService.getAll());
        shuls.sort(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, String> shulNames = shuls.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getName, (a, b) -> a));

        List<ApiKey> keys = apiKeyService.listAll();
        List<KeyRow> keyRows = keys.stream().map(k -> toRow(k, accountsById, shulNames)).toList();
        long active = keys.stream().filter(ApiKey::isActive).count();

        List<AppSession> sessions = appTokenService.listActive();
        List<SessionRow> sessionRows = sessions.stream().map(s -> toRow(s, accountsById)).toList();

        List<OwnerOption> owners = accounts.stream()
                .filter(TNMUser::isEnabled)
                .filter(u -> u.getRoleId() != null && u.role() != null)
                .sorted(Comparator.comparing(TNMUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new OwnerOption(u.getId(), u.getUsername(), u.isSuperAdmin() ? "Super admin" : "Manager",
                        u.isSuperAdmin(), String.join(",", u.getAccessibleOrganizationIds())))
                .toList();

        ModelAndView mv = new ModelAndView("admin/api-keys");
        mv.addObject("user", current);
        mv.addObject("keys", keyRows);
        mv.addObject("sessions", sessionRows);
        mv.addObject("activeKeyCount", active);
        mv.addObject("revokedKeyCount", keys.size() - active);
        mv.addObject("sessionCount", sessionRows.size());
        mv.addObject("owners", owners);
        mv.addObject("allShuls", shuls);
        mv.addObject("allScopes", ApiScope.values());
        mv.addObject("form", form);
        mv.addObject("createError", createError);
        return mv;
    }

    private static KeyRow toRow(ApiKey key, Map<String, TNMUser> accounts, Map<String, String> shulNames) {
        TNMUser owner = accounts.get(key.getAccountId());
        List<String> scopes = key.isSameAsOwner() ? List.of()
                : key.getScopes().stream().map(ApiScope::value).sorted().toList();
        List<String> shuls = key.getRestrictedOrganizationIds() == null ? List.of()
                : key.getRestrictedOrganizationIds().stream()
                        .map(id -> shulNames.getOrDefault(id, "Unknown shul"))
                        .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return new KeyRow(key.getId(), key.getName(), owner == null ? "(deleted account)" : owner.getUsername(),
                key.getDisplayPrefix(), key.isSameAsOwner(), scopes, shuls, format(key.getCreatedAt()),
                key.getLastUsedAt() == null ? "Never" : format(key.getLastUsedAt()), key.isActive(),
                key.getRevokedAt() == null ? null : format(key.getRevokedAt()));
    }

    private static SessionRow toRow(AppSession session, Map<String, TNMUser> accounts) {
        TNMUser owner = accounts.get(session.getAccountId());
        String device = session.getDeviceName() == null || session.getDeviceName().isBlank()
                ? "Unknown device" : session.getDeviceName();
        String method = session.getLoginMethod() == null ? "" : switch (session.getLoginMethod()) {
            case MAGIC_LINK -> "Email link";
            case PASSKEY -> "Passkey";
        };
        return new SessionRow(session.getId(), owner == null ? "(deleted account)" : owner.getUsername(), device,
                method, format(session.getCreatedAt()),
                session.getLastUsedAt() == null ? "Never" : format(session.getLastUsedAt()),
                format(session.getRefreshExpiresAt()));
    }

    private static String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private void requireSuperAdmin() {
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("This page is restricted to super admins.");
        }
    }
}
