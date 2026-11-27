package com.tbdev.teaneckminyanim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.security.AdminAction;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Keeps the admin panel and the Admin API in step. Every admin write handler carries an
 * {@link AdminAction} tag; every tag is either exposed by an {@code /api/v1} endpoint or listed in
 * {@code admin-api-parity-allowlist.txt} with a reason; and every API endpoint is documented.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-api-parity;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "superadmin.email="
})
@AutoConfigureMockMvc
class AdminApiParityTest {

    private static final Set<RequestMethod> WRITE_METHODS =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    /**
     * Sign-in and sign-out routes. They are authentication, not admin actions, so they carry no tag.
     */
    private static final List<String> AUTH_EXCLUSIONS = List.of("/admin/login", "/admin/logout");

    /**
     * pending: admin action not yet in the API. by-design: browser-only on purpose. api-ahead: the API
     * endpoint exists but the admin panel has no handler for it yet.
     */
    private static final Set<String> STATUSES = Set.of("pending", "by-design", "api-ahead");

    private static final String BEARER = "bearerAuth";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RecaptchaService recaptchaService;

    /** One controller method mapped to one or more paths and HTTP methods. */
    private record Handler(HandlerMethod method, Set<String> paths, Set<RequestMethod> httpMethods) {
        String where() {
            return ClassUtils.getUserClass(method.getBeanType()).getSimpleName() + "#" + method.getMethod().getName()
                    + " (" + httpMethods + " " + paths + ")";
        }

        Set<String> tags() {
            AdminAction action = method.getMethodAnnotation(AdminAction.class);
            return action == null ? Set.of() : new LinkedHashSet<>(Arrays.asList(action.value()));
        }

        boolean isWrite() {
            return httpMethods.stream().anyMatch(WRITE_METHODS::contains);
        }
    }

    private List<Handler> handlers() {
        List<Handler> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            Set<String> paths = info.getPathPatternsCondition() == null ? Set.of()
                    : info.getPathPatternsCondition().getPatternValues();
            out.add(new Handler(e.getValue(), paths, info.getMethodsCondition().getMethods()));
        }
        return out;
    }

    private List<Handler> adminWriteHandlers() {
        return handlers().stream()
                .filter(h -> h.isWrite() && h.paths().stream().anyMatch(p -> p.startsWith("/admin")))
                .filter(h -> h.paths().stream().noneMatch(AdminApiParityTest::isAuthRoute))
                .toList();
    }

    private List<Handler> apiHandlers() {
        return handlers().stream()
                .filter(h -> h.paths().stream().anyMatch(p -> p.startsWith("/api/v1")))
                .toList();
    }

    private static boolean isAuthRoute(String path) {
        return AUTH_EXCLUSIONS.stream().anyMatch(x -> path.equals(x) || path.startsWith(x + "/"));
    }

    private Set<String> adminTags() {
        Set<String> tags = new TreeSet<>();
        adminWriteHandlers().forEach(h -> tags.addAll(h.tags()));
        return tags;
    }

    private Set<String> apiTags() {
        Set<String> tags = new TreeSet<>();
        apiHandlers().forEach(h -> tags.addAll(h.tags()));
        return tags;
    }

    private record AllowEntry(String tag, String status, String reason) {
    }

    private record Allowlist(Map<String, AllowEntry> entries, List<String> problems) {
    }

    private Allowlist loadAllowlist() throws Exception {
        String text = new String(new ClassPathResource("admin-api-parity-allowlist.txt").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        Map<String, AllowEntry> entries = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        int n = 0;
        for (String raw : text.split("\\R")) {
            n++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 3) {
                problems.add("line " + n + " is malformed (expected 'tag | status | reason'): " + raw);
                continue;
            }
            String tag = parts[0].strip();
            String status = parts[1].strip();
            String reason = parts[2].strip();
            if (!tag.matches("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*")) {
                problems.add("line " + n + " has an invalid tag '" + tag + "' (use area.verb)");
            } else if (!STATUSES.contains(status)) {
                problems.add("line " + n + " has status '" + status + "' (use pending, by-design or api-ahead)");
            } else if (reason.isEmpty()) {
                problems.add("line " + n + " has no reason");
            } else if (entries.containsKey(tag)) {
                problems.add("line " + n + " duplicates the entry for '" + tag + "'");
            } else {
                entries.put(tag, new AllowEntry(tag, status, reason));
            }
        }
        return new Allowlist(entries, problems);
    }

    @Test
    void everyAdminWriteHandlerIsTagged() {
        List<String> untagged = adminWriteHandlers().stream()
                .filter(h -> h.tags().isEmpty())
                .map(Handler::where)
                .sorted()
                .toList();
        assertTrue(untagged.isEmpty(), () -> "These /admin write handlers have no @AdminAction tag. Add "
                + "@AdminAction(\"area.verb\") to each, using the same tag as the matching /api/v1 endpoint if there is one, "
                + "then either build the API endpoint or add the tag to admin-api-parity-allowlist.txt:\n  "
                + String.join("\n  ", untagged));
    }

    @Test
    void everyAdminTagIsCoveredByTheApiOrAllowlisted() throws Exception {
        Set<String> api = apiTags();
        Allowlist allow = loadAllowlist();
        List<String> missing = adminTags().stream()
                .filter(t -> !api.contains(t) && !allow.entries().containsKey(t))
                .toList();
        assertTrue(missing.isEmpty(), () -> "These admin actions have no /api/v1 endpoint and are not in "
                + "admin-api-parity-allowlist.txt. Build the endpoint with the same @AdminAction tag, or add a line "
                + "'tag | pending | reason' (or 'by-design'):\n  " + String.join("\n  ", missing));
        printSummary(allow);
    }

    @Test
    void allowlistStaysHonest() throws Exception {
        Allowlist allow = loadAllowlist();
        assertTrue(allow.problems().isEmpty(), () -> "Allowlist problems:\n  " + String.join("\n  ", allow.problems()));
        Set<String> api = apiTags();
        Set<String> admin = adminTags();
        Map<String, AllowEntry> admins = new LinkedHashMap<>();
        Map<String, AllowEntry> ahead = new LinkedHashMap<>();
        allow.entries().forEach((t, e) -> (e.status().equals("api-ahead") ? ahead : admins).put(t, e));
        List<String> covered = admins.keySet().stream().filter(api::contains).toList();
        assertTrue(covered.isEmpty(), () -> "These allowlisted tags are now covered by an API endpoint; remove them "
                + "from admin-api-parity-allowlist.txt: " + covered);
        List<String> unused = admins.keySet().stream().filter(t -> !admin.contains(t)).toList();
        assertTrue(unused.isEmpty(), () -> "These allowlisted tags are used by no /admin handler; remove them or fix "
                + "the typo in admin-api-parity-allowlist.txt: " + unused);
        List<String> caughtUp = ahead.keySet().stream().filter(admin::contains).toList();
        assertTrue(caughtUp.isEmpty(), () -> "These api-ahead tags now have an admin handler; remove them from "
                + "admin-api-parity-allowlist.txt: " + caughtUp);
        List<String> noApi = ahead.keySet().stream().filter(t -> !api.contains(t)).toList();
        assertTrue(noApi.isEmpty(), () -> "These api-ahead tags are used by no /api/v1 handler; remove them or fix "
                + "the typo in admin-api-parity-allowlist.txt: " + noApi);
    }

    @Test
    void everyApiTagMatchesAnAdminHandler() throws Exception {
        Set<String> admin = adminTags();
        Allowlist allow = loadAllowlist();
        List<String> unknown = apiTags().stream().filter(t -> !admin.contains(t))
                .filter(t -> allow.entries().get(t) == null || !allow.entries().get(t).status().equals("api-ahead"))
                .toList();
        assertTrue(unknown.isEmpty(), () -> "These @AdminAction tags on /api/v1 handlers match no /admin write handler "
                + "(typo?). If the admin panel is genuinely behind, list the tag as 'api-ahead' in the allowlist: " + unknown);
    }

    @Test
    void everyApiEndpointIsDocumented() {
        List<String> problems = new ArrayList<>();
        for (Handler h : apiHandlers()) {
            Operation op = h.method().getMethodAnnotation(Operation.class);
            if (op == null || op.summary().isBlank()) {
                problems.add(h.where() + " needs @Operation with a non-blank summary");
            }
            if (requiresBearer(h) && !declaresBearer(h)) {
                problems.add(h.where() + " needs @SecurityRequirement(name = \"bearerAuth\") on the method or class");
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    private static boolean requiresBearer(Handler h) {
        String pkg = ClassUtils.getUserClass(h.method().getBeanType()).getPackageName();
        boolean notificationWrite = h.isWrite()
                && h.paths().stream().anyMatch(p -> p.startsWith("/api/v1/notifications"));
        return !h.tags().isEmpty()
                || pkg.equals("com.tbdev.teaneckminyanim.api.admin")
                || h.paths().contains("/api/v1/me")
                || notificationWrite;
    }

    private static boolean declaresBearer(Handler h) {
        Class<?> type = ClassUtils.getUserClass(h.method().getBeanType());
        return Arrays.stream(h.method().getMethod().getAnnotationsByType(SecurityRequirement.class))
                .anyMatch(r -> BEARER.equals(r.name()))
                || Arrays.stream(type.getAnnotationsByType(SecurityRequirement.class))
                .anyMatch(r -> BEARER.equals(r.name()));
    }

    @Test
    void generatedSpecDeclaresBearerAuthOnTheRightOperations() throws Exception {
        String body = mvc.perform(get("/api/docs.json")).andReturn().getResponse().getContentAsString();
        JsonNode spec = new ObjectMapper().readTree(body);

        JsonNode scheme = spec.at("/components/securitySchemes/bearerAuth");
        assertEquals("http", scheme.path("type").asText(), "bearerAuth scheme type");
        assertEquals("bearer", scheme.path("scheme").asText().toLowerCase(), "bearerAuth scheme");

        List<String> problems = new ArrayList<>();
        int writes = 0;
        Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> p = paths.next();
            String path = p.getKey();
            boolean adminArea = path.startsWith("/api/v1/organizations/") || path.equals("/api/v1/notifications")
                    || path.startsWith("/api/v1/notifications/");
            if (!adminArea) {
                continue;
            }
            for (String m : List.of("post", "put", "patch", "delete")) {
                JsonNode op = p.getValue().path(m);
                if (op.isMissingNode()) {
                    continue;
                }
                writes++;
                if (!hasBearer(op)) {
                    problems.add(m.toUpperCase() + " " + path + " has no bearerAuth security requirement");
                }
            }
        }
        assertTrue(writes > 0, "expected admin write operations in the spec");
        for (String publicGet : List.of("/api/v1/schedule", "/api/v1/organizations", "/api/v1/zmanim")) {
            JsonNode op = spec.path("paths").path(publicGet).path("get");
            assertFalse(op.isMissingNode(), publicGet + " missing from the spec");
            if (hasBearer(op)) {
                problems.add("GET " + publicGet + " must be public but requires bearerAuth");
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    private static boolean hasBearer(JsonNode op) {
        for (JsonNode req : op.path("security")) {
            if (req.has(BEARER)) {
                return true;
            }
        }
        return false;
    }

    private void printSummary(Allowlist allow) {
        Set<String> admin = adminTags();
        Set<String> api = apiTags();
        long covered = admin.stream().filter(api::contains).count();
        long pending = admin.stream().filter(t -> !api.contains(t) && allow.entries().containsKey(t)
                && allow.entries().get(t).status().equals("pending")).count();
        long byDesign = admin.stream().filter(t -> !api.contains(t) && allow.entries().containsKey(t)
                && allow.entries().get(t).status().equals("by-design")).count();
        long ahead = allow.entries().values().stream().filter(e -> e.status().equals("api-ahead")).count();
        System.out.printf("ADMIN/API PARITY: %d admin actions, %d covered by the API, %d pending, %d by design, "
                + "%d api-ahead (API only)%n", admin.size(), covered, pending, byDesign, ahead);
    }
}
