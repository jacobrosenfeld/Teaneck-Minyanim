package com.tbdev.teaneckminyanim.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.EventSource;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.AppSession;
import com.tbdev.teaneckminyanim.model.CalendarEvent;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.repo.LocationRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.TNMUserService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.auth.AppTokenService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manual-override admin API through the real stack on H2: create, patch and delete are visible in the public
 * per-shul schedule immediately, and the auth rules (no token, wrong scope, restricted key, other shul's manager)
 * and error mapping hold.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:override-admin-api;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email="
})
@AutoConfigureMockMvc
class OverrideAdminApiIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE = "/api/v1/organizations/ORGA/overrides";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private LocationRepository locations;
    @Autowired private CalendarEventRepository events;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private TNMUserService users;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private AppTokenService appTokens;

    @MockitoBean private RecaptchaService recaptchaService;

    private int ipCounter;
    private LocalDate day;
    private String locationA;
    private String locationB;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        events.deleteAll();
        apiKeyRepository.deleteAll();
        appSessions.deleteAll();
        userRepository.deleteAll();
        locations.deleteAll();
        organizations.deleteAll();
        organizations.save(shul("ORGA", "Shul A", "shul-a", true));
        organizations.save(shul("ORGB", "Shul B", "shul-b", true));
        organizations.save(shul("ORGD", "Shul D", "shul-d", false));
        userRepository.save(account("SUPER1", "superadmin", null));
        userRepository.save(account("MGR1", "manager", "ORGA"));
        userRepository.save(account("MGR2", "manager2", "ORGB"));
        locationA = locations.save(new Location("Main hall", "ORGA")).getId();
        locationB = locations.save(new Location("Other hall", "ORGB")).getId();
        day = LocalDate.now().plusDays(2);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- create / read / patch / delete, with the public schedule following along ---------------------------

    @Test
    void createPatchAndDeleteShowUpInThePublicScheduleImmediately() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ), Set.of());

        MvcResult created = mvc.perform(api(post(BASE)).header("Authorization", bearer(key))
                        .contentType("application/json")
                        .content("""
                                {"date":"%s","endDate":"%s","startTime":"18:45","minyanType":"MINCHA",
                                 "overrideMode":"ADDITIVE","locationId":"%s","notes":"Special","nusach":"SEFARD"}
                                """.formatted(day, day.plusDays(1), locationA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].date").value(day.toString()))
                .andExpect(jsonPath("$.data[0].startTime").value("18:45"))
                .andExpect(jsonPath("$.data[0].minyanType").value("MINCHA"))
                .andExpect(jsonPath("$.data[0].overrideMode").value("ADDITIVE"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].notes").value("Special"))
                .andExpect(jsonPath("$.data[0].locationId").value(locationA))
                .andExpect(jsonPath("$.data[0].locationName").value("Main hall"))
                .andExpect(jsonPath("$.data[0].nusach").value("SEFARD"))
                .andExpect(jsonPath("$.data[0].editedBy").value("manager"))
                .andExpect(jsonPath("$.data[0].organizationId").value("ORGA"))
                .andExpect(jsonPath("$.data[0].sourceRef").doesNotExist())
                .andExpect(jsonPath("$.meta.created").value(2))
                .andReturn();
        String id = body(created).at("/data/0/id").asText();

        JsonNode scheduled = manualRowsInSchedule(key, day);
        assertThat(scheduled).hasSize(1);
        assertThat(scheduled.get(0).get("startTime").asText()).isEqualTo("18:45");
        assertThat(scheduled.get(0).get("notes").asText()).isEqualTo("Special");

        // list
        mvc.perform(api(get(BASE)).header("Authorization", bearer(key))
                        .param("start", day.toString()).param("end", day.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.count").value(2))
                .andExpect(jsonPath("$.meta.start").value(day.toString()))
                .andExpect(jsonPath("$.meta.end").value(day.plusDays(1).toString()));

        // patch time + notes
        mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                        .contentType("application/json")
                        .content("{\"startTime\":\"19:10\",\"notes\":\"Moved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.startTime").value("19:10"))
                .andExpect(jsonPath("$.data.notes").value("Moved"))
                .andExpect(jsonPath("$.data.locationName").value("Main hall"));
        JsonNode moved = manualRowsInSchedule(key, day);
        assertThat(moved).hasSize(1);
        assertThat(moved.get(0).get("startTime").asText()).isEqualTo("19:10");
        assertThat(moved.get(0).get("notes").asText()).isEqualTo("Moved");

        // patch enabled=false hides it from the schedule, enabled=true brings it back
        mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(manualRowsInSchedule(key, day)).isEmpty();
        mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                        .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        assertThat(manualRowsInSchedule(key, day)).hasSize(1);

        // delete
        mvc.perform(api(delete(BASE + "/" + id)).header("Authorization", bearer(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.id").value(id));
        assertThat(manualRowsInSchedule(key, day)).isEmpty();
        assertThat(manualRowsInSchedule(key, day.plusDays(1))).hasSize(1);
        mvc.perform(api(delete(BASE + "/" + id)).header("Authorization", bearer(key)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void fullDayReplaceHidesOtherSourcesInTheSchedule() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        events.save(row("ORGA", EventSource.RULES, LocalTime.of(7, 0), MinyanType.SHACHARIS));

        mvc.perform(api(post(BASE)).header("Authorization", bearer(key)).contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"09:00\",\"minyanType\":\"SHACHARIS\",\"overrideMode\":\"FULL_DAY_REPLACE\"}"
                                .formatted(day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].overrideMode").value("FULL_DAY_REPLACE"));

        JsonNode schedule = schedule(key, day);
        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).get("source").asText()).isEqualTo("MANUAL");
    }

    @Test
    void appTokenOfAManagerCanWriteToo() throws Exception {
        AppTokenService.IssuedTokens tokens =
                appTokens.issue(users.findById("MGR1"), AppSession.LoginMethod.MAGIC_LINK, "phone", null);
        mvc.perform(api(post(BASE)).header("Authorization", bearer(tokens.accessToken()))
                        .contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].nusach").value("UNSPECIFIED"));
    }

    @Test
    void readOnlyKeyCannotWriteButCanListAndSuperAdminKeyWorksOnAnyShul() throws Exception {
        String readOnly = key("MGR1", Set.of(ApiScope.SCHEDULE_READ), Set.of());
        mvc.perform(api(get(BASE)).header("Authorization", bearer(readOnly))
                        .param("start", day.toString()).param("end", day.toString()))
                .andExpect(status().isOk());

        String superKey = key("SUPER1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        mvc.perform(api(post("/api/v1/organizations/shul-b/overrides")).header("Authorization", bearer(superKey))
                        .contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].organizationId").value("ORGB"));
    }

    // --- authentication and authorization --------------------------------------------------------------------

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mvc.perform(api(post(BASE)).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(api(get(BASE)).param("start", day.toString()).param("end", day.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(patch(BASE + "/1")).contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(delete(BASE + "/1"))).andExpect(status().isUnauthorized());
    }

    @Test
    void keyWithoutOverridesWriteScopeIsInsufficient() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.SCHEDULE_READ), Set.of());
        String id = String.valueOf(events.save(row("ORGA", EventSource.MANUAL, LocalTime.of(8, 0), MinyanType.SHACHARIS)).getId());

        mvc.perform(api(post(BASE)).header("Authorization", bearer(key)).contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
        mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key)).contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
        mvc.perform(api(delete(BASE + "/" + id)).header("Authorization", bearer(key)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
        assertThat(events.findById(Long.valueOf(id))).isPresent();
    }

    @Test
    void keyWithoutScheduleReadCannotList() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        mvc.perform(api(get(BASE)).header("Authorization", bearer(key))
                        .param("start", day.toString()).param("end", day.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
    }

    @Test
    void keyRestrictedToAnotherShulIsForbidden() throws Exception {
        String key = key("SUPER1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of("ORGB"));
        mvc.perform(api(post(BASE)).header("Authorization", bearer(key)).contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        assertThat(events.findAll()).isEmpty();
    }

    @Test
    void managerOfAnotherShulIsForbidden() throws Exception {
        String key = key("MGR2", Set.of(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ), Set.of());
        mvc.perform(api(post(BASE)).header("Authorization", bearer(key)).contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(api(get(BASE)).header("Authorization", bearer(key))
                        .param("start", day.toString()).param("end", day.toString()))
                .andExpect(status().isForbidden());
        assertThat(events.findAll()).isEmpty();
    }

    @Test
    void anotherShulsEventIsNotFoundAndLeftAlone() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        String foreign = String.valueOf(events.save(row("ORGB", EventSource.MANUAL, LocalTime.of(8, 0), MinyanType.SHACHARIS)).getId());

        mvc.perform(api(patch(BASE + "/" + foreign)).header("Authorization", bearer(key))
                        .contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(api(delete(BASE + "/" + foreign)).header("Authorization", bearer(key)))
                .andExpect(status().isNotFound());
        mvc.perform(api(delete(BASE + "/not-a-number")).header("Authorization", bearer(key)))
                .andExpect(status().isNotFound());
        assertThat(events.findById(Long.valueOf(foreign))).get().satisfies(e -> assertThat(e.isEnabled()).isTrue());
    }

    @Test
    void unknownOrDisabledShulIsNotFound() throws Exception {
        String key = key("SUPER1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        for (String shul : List.of("nope", "shul-d")) {
            mvc.perform(api(post("/api/v1/organizations/" + shul + "/overrides"))
                            .header("Authorization", bearer(key)).contentType("application/json")
                            .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    // --- RULES / IMPORTED rows and bad input -------------------------------------------------------------------

    @Test
    void rulesAndImportedRowsAreConflicts() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        for (EventSource source : List.of(EventSource.RULES, EventSource.IMPORTED)) {
            CalendarEvent saved = events.save(row("ORGA", source, LocalTime.of(7, 0), MinyanType.SHACHARIS));
            String id = String.valueOf(saved.getId());

            mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                            .contentType("application/json").content("{\"enabled\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("NOT_MANUAL_OVERRIDE"));
            mvc.perform(api(delete(BASE + "/" + id)).header("Authorization", bearer(key)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("NOT_MANUAL_OVERRIDE"));
            assertThat(events.findById(saved.getId())).get().satisfies(e -> assertThat(e.isEnabled()).isTrue());
        }
    }

    @Test
    void badInputIsAValidationError() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE, ApiScope.SCHEDULE_READ), Set.of());
        List<String> bad = List.of(
                "{\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}",
                "{\"date\":\"tomorrow\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}",
                "{\"date\":\"%s\",\"startTime\":\"8pm\",\"minyanType\":\"MAARIV\"}".formatted(day),
                "{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"NOPE\"}".formatted(day),
                "{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\",\"nusach\":\"NOPE\"}".formatted(day),
                "{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\",\"overrideMode\":\"SIDEWAYS\"}".formatted(day),
                "{\"date\":\"%s\",\"endDate\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day, day.minusDays(1)),
                "{\"date\":\"%s\",\"endDate\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\"}".formatted(day, day.plusDays(62)),
                "{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\",\"locationId\":\"%s\"}".formatted(day, locationB),
                "{not json");
        for (String json : bad) {
            mvc.perform(api(post(BASE)).header("Authorization", bearer(key)).contentType("application/json").content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        assertThat(events.findAll()).isEmpty();

        String id = String.valueOf(events.save(row("ORGA", EventSource.MANUAL, LocalTime.of(8, 0), MinyanType.SHACHARIS)).getId());
        for (String json : List.of("{}", "{\"startTime\":\"late\"}", "{\"minyanType\":\"MAARIV\"}",
                "{\"locationId\":\"" + locationB + "\"}", "{\"nusach\":\"NOPE\"}")) {
            mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                            .contentType("application/json").content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        mvc.perform(api(get(BASE)).header("Authorization", bearer(key))
                        .param("start", day.toString()).param("end", day.plusDays(62).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(api(get(BASE)).header("Authorization", bearer(key)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patchCanClearNotesAndLocation() throws Exception {
        String key = key("MGR1", Set.of(ApiScope.OVERRIDES_WRITE), Set.of());
        MvcResult created = mvc.perform(api(post(BASE)).header("Authorization", bearer(key))
                        .contentType("application/json")
                        .content("{\"date\":\"%s\",\"startTime\":\"20:00\",\"minyanType\":\"MAARIV\",\"notes\":\"n\",\"locationId\":\"%s\"}"
                                .formatted(day, locationA)))
                .andExpect(status().isCreated()).andReturn();
        String id = body(created).at("/data/0/id").asText();

        mvc.perform(api(patch(BASE + "/" + id)).header("Authorization", bearer(key))
                        .contentType("application/json").content("{\"notes\":\"\",\"locationId\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").doesNotExist())
                .andExpect(jsonPath("$.data.locationId").doesNotExist())
                .andExpect(jsonPath("$.data.locationName").doesNotExist());
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private JsonNode schedule(String key, LocalDate date) throws Exception {
        MvcResult result = mvc.perform(api(get("/api/v1/organizations/ORGA/schedule"))
                        .header("Authorization", bearer(key)).param("date", date.toString()))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("data");
    }

    private JsonNode manualRowsInSchedule(String key, LocalDate date) throws Exception {
        var rows = JSON.createArrayNode();
        for (JsonNode node : schedule(key, date)) {
            if ("MANUAL".equals(node.get("source").asText())) rows.add(node);
        }
        return rows;
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        // A fresh client IP per request keeps these tests clear of the write rate limit.
        return builder.with(remote("10.7." + (ipCounter / 250) + "." + (ipCounter++ % 250 + 1)));
    }

    private static RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String key(String owner, Set<ApiScope> scopes, Set<String> restriction) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        try {
            return apiKeys.create(owner, "test key", false, scopes, restriction).rawKey();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CalendarEvent row(String orgId, EventSource source, LocalTime time, MinyanType type) {
        return CalendarEvent.builder()
                .organizationId(orgId).date(day).startTime(time).minyanType(type).source(source)
                .sourceRef(source == EventSource.MANUAL ? "manual:ADDITIVE:seed" : "seed")
                .nusach(Nusach.ASHKENAZ).enabled(true)
                .build();
    }

    private static Organization shul(String id, String name, String slug, boolean enabled) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setUrlSlug(slug);
        organization.setEnabled(enabled);
        organization.setOrgColor("#275ed8");
        return organization;
    }

    private static TNMUser account(String id, String username, String organizationId) {
        return TNMUser.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .encryptedPassword(new BCryptPasswordEncoder().encode("correct-horse-battery"))
                .organizationId(organizationId)
                .roleId(Role.ADMIN.getId())
                .enabled(true)
                .build();
    }
}
