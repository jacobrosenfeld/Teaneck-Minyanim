package com.tbdev.teaneckminyanim.service.auth;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.model.ApiKey;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.security.ApiAuthentication;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Personal API keys ({@code tnm_...}). Creating, revoking and listing keys is limited to super admins.
 * Only the SHA-256 hash of a key is stored; the raw key is returned once, from {@link #create}.
 */
@Service
@Slf4j
public class ApiKeyService {
    public static final int MAX_NAME_LENGTH = 100;
    /** lastUsedAt is written at most this often per key, to avoid a DB write on every request. */
    static final Duration LAST_USED_WRITE_INTERVAL = Duration.ofMinutes(1);

    public record CreatedApiKey(ApiKey key, String rawKey) {
        @Override
        public String toString() {
            return "CreatedApiKey[key=" + key + ", rawKey=<redacted>]";
        }
    }

    private final ApiKeyRepository repository;
    private final TNMUserService userService;
    private final OrganizationRepository organizations;
    private final AuthAuditService auditService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiKeyService(ApiKeyRepository repository, TNMUserService userService,
                         OrganizationRepository organizations, AuthAuditService auditService) {
        this(repository, userService, organizations, auditService, Clock.systemDefaultZone());
    }

    ApiKeyService(ApiKeyRepository repository, TNMUserService userService, OrganizationRepository organizations,
                  AuthAuditService auditService, Clock clock) {
        this.repository = repository;
        this.userService = userService;
        this.organizations = organizations;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Creates a key for {@code ownerAccountId}. Super admins only.
     *
     * @param sameAsOwner      when true the key carries every scope and {@code scopes} is ignored
     * @param scopes           required (non-empty) unless {@code sameAsOwner}
     * @param restrictedOrgIds optional shul restriction; must be shuls the owner can access. Empty or null
     *                         means every shul the owner has.
     * @throws AccessDeniedException    when the current user is not a super admin
     * @throws IllegalArgumentException for invalid input (message is safe to show)
     */
    @Transactional
    public CreatedApiKey create(String ownerAccountId, String name, boolean sameAsOwner, Set<ApiScope> scopes,
                                Set<String> restrictedOrgIds) {
        TNMUser actor = requireSuperAdmin();
        TNMUser owner = ownerAccountId == null ? null : userService.findById(ownerAccountId);
        if (owner == null) {
            throw new IllegalArgumentException("Choose an existing account to own the key.");
        }
        if (owner.isDisabled() || owner.getRoleId() == null || owner.role() == null) {
            throw new IllegalArgumentException("API keys can only be created for enabled accounts.");
        }
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Give the key a name.");
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Key names can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        Set<ApiScope> keyScopes = sameAsOwner || scopes == null || scopes.isEmpty()
                ? EnumSet.noneOf(ApiScope.class) : EnumSet.copyOf(scopes);
        if (!sameAsOwner && keyScopes.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one scope, or \"Same as owner\".");
        }
        Set<String> restriction = normalizeRestriction(restrictedOrgIds);
        for (String orgId : restriction) {
            boolean allowed = owner.isSuperAdmin() ? organizations.existsById(orgId) : owner.canAccessOrganization(orgId);
            if (!allowed) {
                throw new IllegalArgumentException("A key can only be limited to shuls its owner can access.");
            }
        }

        String rawKey = SecureTokens.newApiKey();
        ApiKey key = ApiKey.builder()
                .id("K" + UUID.randomUUID().toString().replace("-", ""))
                .accountId(owner.getId())
                .name(trimmedName)
                .displayPrefix(rawKey.substring(0, SecureTokens.API_KEY_DISPLAY_PREFIX_LENGTH))
                .keyHash(AuthAuditService.hash(rawKey))
                .sameAsOwner(sameAsOwner)
                .scopes(keyScopes)
                .restrictedOrganizationIds(restriction)
                .createdAt(now())
                .createdByAccountId(actor.getId())
                .build();
        ApiKey saved = repository.save(key);
        auditService.record("API_KEY_CREATED", owner.getId(), AuthRequests.currentClientIpHash(),
                "key=" + saved.getId() + " by=" + actor.getId() + " sameAsOwner=" + sameAsOwner
                        + " scopes=" + scopeList(keyScopes) + " shuls=" + String.join(",", restriction));
        return new CreatedApiKey(saved, rawKey);
    }

    /**
     * Revokes a key. Super admins only. Revoking an already revoked key is a no-op.
     *
     * @throws IllegalArgumentException when no key has this id
     */
    @Transactional
    public ApiKey revoke(String id) {
        TNMUser actor = requireSuperAdmin();
        ApiKey key = (id == null ? Optional.<ApiKey>empty() : repository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("API key not found."));
        if (key.isRevoked()) {
            return key;
        }
        key.setRevokedAt(now());
        key.setRevokedByAccountId(actor.getId());
        key.setRevokedReason("MANUAL");
        ApiKey saved = repository.save(key);
        auditService.record("API_KEY_REVOKED", key.getAccountId(), AuthRequests.currentClientIpHash(),
                "key=" + key.getId() + " by=" + actor.getId() + " reason=MANUAL");
        return saved;
    }

    /** Every key, newest first. Super admins only. */
    public List<ApiKey> listAll() {
        requireSuperAdmin();
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /** The keys owned by one account, newest first. Super admins only. */
    public List<ApiKey> listForAccount(String accountId) {
        requireSuperAdmin();
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    /** Resolves a raw key using the current thread's request for the last-used IP. */
    public Optional<ApiKey> resolve(String rawKey) {
        return resolve(rawKey, AuthRequests.currentRequest());
    }

    /**
     * Looks up a usable (well-formed, known, not revoked) key and records its use. The owner is NOT checked
     * here; the bearer filter checks the live owner account on every request.
     */
    public Optional<ApiKey> resolve(String rawKey, HttpServletRequest request) {
        if (!SecureTokens.looksLikeApiKey(rawKey)) {
            return Optional.empty();
        }
        Optional<ApiKey> found = repository.findByKeyHash(AuthAuditService.hash(rawKey)).filter(ApiKey::isActive);
        found.ifPresent(key -> markUsed(key, request));
        return found;
    }

    /**
     * Revokes every active key owned by {@code accountId}. Not gated: called by account lifecycle code
     * (disabling a user). Returns how many keys were revoked.
     */
    @Transactional
    public int revokeAllForAccount(String accountId, String reason) {
        if (accountId == null) return 0;
        TNMUser actor = userService.getCurrentUser();
        int revoked = repository.revokeAllActiveForAccount(accountId, now(), actor == null ? null : actor.getId(),
                reason == null ? "REVOKED" : reason);
        if (revoked > 0) {
            auditService.record("API_KEY_REVOKED", accountId, AuthRequests.currentClientIpHash(),
                    "all keys count=" + revoked + " reason=" + reason);
        }
        return revoked;
    }

    /** Deletes every key owned by {@code accountId} (account deletion). Returns how many were deleted. */
    @Transactional
    public long deleteAllForAccount(String accountId) {
        if (accountId == null) return 0;
        return repository.deleteByAccountId(accountId);
    }

    private void markUsed(ApiKey key, HttpServletRequest request) {
        LocalDateTime now = now();
        if (key.getLastUsedAt() != null && key.getLastUsedAt().isAfter(now.minus(LAST_USED_WRITE_INTERVAL))) {
            return;
        }
        try {
            repository.markUsed(key.getId(), now, AuthRequests.clientIpHash(request));
            key.setLastUsedAt(now);
        } catch (RuntimeException e) {
            log.warn("Could not record use of API key {}: {}", key.getId(), e.getMessage());
        }
    }

    private TNMUser requireSuperAdmin() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof ApiAuthentication api && api.isApiKey()) {
            // A key must never be able to mint or revoke keys, even a super admin's same-as-owner key.
            throw new AccessDeniedException("API keys cannot manage API keys.");
        }
        if (!userService.isSuperAdmin()) {
            throw new AccessDeniedException("Only super admins can manage API keys.");
        }
        return userService.getCurrentUser();
    }

    private static Set<String> normalizeRestriction(Set<String> ids) {
        Set<String> result = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isBlank()) result.add(id.trim());
            }
        }
        return result;
    }

    private static String scopeList(Set<ApiScope> scopes) {
        return scopes.stream().map(ApiScope::value).collect(Collectors.joining(","));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
