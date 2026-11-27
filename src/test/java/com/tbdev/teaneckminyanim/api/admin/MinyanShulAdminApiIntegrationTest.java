package com.tbdev.teaneckminyanim.api.admin;

import com.tbdev.teaneckminyanim.enums.ApiScope;
import com.tbdev.teaneckminyanim.enums.Nusach;
import com.tbdev.teaneckminyanim.enums.Role;
import com.tbdev.teaneckminyanim.minyan.MinyanType;
import com.tbdev.teaneckminyanim.model.Location;
import com.tbdev.teaneckminyanim.model.Minyan;
import com.tbdev.teaneckminyanim.model.Organization;
import com.tbdev.teaneckminyanim.model.TNMUser;
import com.tbdev.teaneckminyanim.repo.ApiKeyRepository;
import com.tbdev.teaneckminyanim.repo.AppSessionRepository;
import com.tbdev.teaneckminyanim.repo.CalendarEventRepository;
import com.tbdev.teaneckminyanim.repo.LocationRepository;
import com.tbdev.teaneckminyanim.repo.MinyanRepository;
import com.tbdev.teaneckminyanim.repo.OrganizationRepository;
import com.tbdev.teaneckminyanim.repo.TNMUserRepository;
import com.tbdev.teaneckminyanim.service.CalendarMaterializationService;
import com.tbdev.teaneckminyanim.service.auth.ApiKeyService;
import com.tbdev.teaneckminyanim.service.recaptcha.RecaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The minyanim and shul admin API through the real application stack on H2: reads, partial updates,
 * immediate effect on the public schedule, validation, and the authorization matrix.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:minyan-shul-admin-api;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "logging.level.org.springframework.web=INFO",
        "superadmin.email="
})
@AutoConfigureMockMvc
class MinyanShulAdminApiIntegrationTest {
    private static final String MINYAN_ID = "MIN-A1";
    private static final String OTHER_MINYAN_ID = "MIN-B1";
    private static final String BASE = "/api/v1/organizations/ORGA";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TNMUserRepository userRepository;
    @Autowired private OrganizationRepository organizations;
    @Autowired private LocationRepository locations;
    @Autowired private MinyanRepository minyanim;
    @Autowired private CalendarEventRepository calendarEvents;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AppSessionRepository appSessions;
    @Autowired private ApiKeyService apiKeys;
    @Autowired private CalendarMaterializationService materialization;

    @MockitoBean private RecaptchaService recaptchaService;

    private int ipCounter;
    private String managerKey;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM SPRING_SESSION");
        jdbc.update("DELETE FROM AUTH_AUDIT_EVENT");
        apiKeyRepository.deleteAll();
        appSessions.deleteAll();
        calendarEvents.deleteAll();
        minyanim.deleteAll();
        locations.deleteAll();
        userRepository.deleteAll();
        organizations.deleteAll();

        organizations.save(shul("ORGA", "Shul A", "shul-a"));
        organizations.save(shul("ORGB", "Shul B", "shul-b"));
        locations.save(new Location("LOC-A1", "Main Sanctuary", "ORGA"));
        locations.save(new Location("LOC-A2", "Beis Medrash", "ORGA"));
        locations.save(new Location("LOC-B1", "Other Hall", "ORGB"));
        minyanim.save(minyan(MINYAN_ID, "ORGA", "LOC-A1", "T7:0:0:0"));
        minyanim.save(minyan(OTHER_MINYAN_ID, "ORGB", "LOC-B1", "T7:0:0:0"));
        userRepository.save(account("SUPER1", "superadmin", null));
        userRepository.save(account("MGR1", "manager", "ORGA"));
        userRepository.save(account("MGR2", "manager-b", "ORGB"));

        materialization.syncRulesForOrganizationLive("ORGA");
        managerKey = createKey("manager", Set.of(ApiScope.SCHEDULE_READ, ApiScope.MINYANIM_WRITE, ApiScope.SHUL_WRITE), Set.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // --- reads -----------------------------------------------------------------------------------------

    @Test
    void listIncludesDisabledMinyanimWithFullScheduleShape() throws Exception {
        Minyan disabled = minyan("MIN-A2", "ORGA", "LOC-A2", "NM");
        disabled.setEnabled(false);
        disabled.setStartTime2("RNETZ:-5");
        disabled.setStartTime3("QPLAG_HAMINCHA:-10");
        minyanim.save(disabled);

        mvc.perform(api(get(BASE + "/minyanim")).header("Authorization", bearer(managerKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.count").value(2))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A1')].locationName").value("Main Sanctuary"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A1')].schedule.sunday.type").value("FIXED"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A1')].schedule.sunday.time").value("07:00"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].enabled").value(false))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.sunday.type").value("NONE"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.monday.type").value("DYNAMIC"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.monday.zman").value("NETZ"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.monday.offsetMinutes").value(-5))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.tuesday.type").value("ROUNDED"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.tuesday.zman").value("PLAG_HAMINCHA"))
                .andExpect(jsonPath("$.data[?(@.id=='MIN-A2')].schedule.tuesday.offsetMinutes").value(-10));
    }

    @Test
    void getReturnsOneMinyanAndHidesOtherShuls() throws Exception {
        mvc.perform(api(get(BASE + "/minyanim/" + MINYAN_ID)).header("Authorization", bearer(managerKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(MINYAN_ID))
                .andExpect(jsonPath("$.data.type").value("SHACHARIS"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.nusach").value("ASHKENAZ"))
                .andExpect(jsonPath("$.data.schedule.roshChodeshChanuka.type").value("FIXED"));

        // A minyan of another shul is not found through this shul's path.
        mvc.perform(api(get(BASE + "/minyanim/" + OTHER_MINYAN_ID)).header("Authorization", bearer(managerKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(api(get(BASE + "/minyanim/NOPE")).header("Authorization", bearer(managerKey)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownShulIs404() throws Exception {
        mvc.perform(api(get("/api/v1/organizations/nope/minyanim")).header("Authorization", bearer(managerKey)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(api(patch("/api/v1/organizations/nope/minyanim/" + MINYAN_ID))
                        .header("Authorization", bearer(managerKey)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(api(patch("/api/v1/organizations/nope")).header("Authorization", bearer(managerKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shulCanBeAddressedBySlug() throws Exception {
        mvc.perform(api(get("/api/v1/organizations/shul-a/minyanim")).header("Authorization", bearer(managerKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // --- writes: enable/disable and the public schedule ------------------------------------------------

    @Test
    void disableRemovesMinyanFromPublicScheduleAndEnableRestoresIt() throws Exception {
        assertThat(publicEventCount()).isPositive();

        mvc.perform(patchMinyan(managerKey, "{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(publicEventCount()).isZero();

        mvc.perform(patchMinyan(managerKey, "{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
        assertThat(publicEventCount()).isPositive();
    }

    @Test
    void schedulePatchChangesMaterializedTimesAndKeepsOtherSlots() throws Exception {
        String allSlots = "{\"type\":\"FIXED\",\"time\":\"08:15\"}";
        String body = "{\"schedule\":{\"sunday\":%1$s,\"monday\":%1$s,\"tuesday\":%1$s,\"wednesday\":%1$s,\"thursday\":%1$s,"
                + "\"friday\":%1$s,\"shabbos\":%1$s,\"roshChodesh\":%1$s,\"yomTov\":%1$s,\"chanuka\":%1$s,\"roshChodeshChanuka\":%1$s}}";
        mvc.perform(patchMinyan(managerKey, body.formatted(allSlots)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.sunday.time").value("08:15"));
        mockPublicTimes("08:15");

        // A partial schedule changes only the slot that is sent.
        mvc.perform(patchMinyan(managerKey,
                        "{\"schedule\":{\"monday\":{\"type\":\"DYNAMIC\",\"zman\":\"netz\",\"offsetMinutes\":-5},\"friday\":{\"type\":\"NONE\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.monday.type").value("DYNAMIC"))
                .andExpect(jsonPath("$.data.schedule.monday.zman").value("NETZ"))
                .andExpect(jsonPath("$.data.schedule.monday.offsetMinutes").value(-5))
                .andExpect(jsonPath("$.data.schedule.friday.type").value("NONE"))
                .andExpect(jsonPath("$.data.schedule.sunday.time").value("08:15"))
                .andExpect(jsonPath("$.data.schedule.shabbos.time").value("08:15"));

        mvc.perform(api(get(BASE + "/minyanim/" + MINYAN_ID)).header("Authorization", bearer(managerKey)))
                .andExpect(jsonPath("$.data.schedule.monday.zman").value("NETZ"))
                .andExpect(jsonPath("$.data.schedule.tuesday.time").value("08:15"));
    }

    @Test
    void detailsCanBeChangedAndCleared() throws Exception {
        mvc.perform(patchMinyan(managerKey,
                        "{\"notes\":\"Early minyan\",\"locationId\":\"LOC-A2\",\"nusach\":\"sefard\",\"whatsapp\":\"https://chat.example/x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").value("Early minyan"))
                .andExpect(jsonPath("$.data.locationId").value("LOC-A2"))
                .andExpect(jsonPath("$.data.locationName").value("Beis Medrash"))
                .andExpect(jsonPath("$.data.nusach").value("SEFARD"))
                .andExpect(jsonPath("$.data.whatsapp").value("https://chat.example/x"));

        mvc.perform(patchMinyan(managerKey, "{\"notes\":\"\",\"whatsapp\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notes").doesNotExist())
                .andExpect(jsonPath("$.data.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.data.locationId").value("LOC-A2"));
    }

    @Test
    void emptyPatchChangesNothing() throws Exception {
        mvc.perform(patchMinyan(managerKey, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(MINYAN_ID))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    // --- validation ------------------------------------------------------------------------------------

    @Test
    void invalidRequestsAreRejectedAndChangeNothing() throws Exception {
        List<String> badBodies = List.of(
                "{\"enabled\":false,\"schedule\":{\"sunday\":{\"type\":\"FIXED\",\"time\":\"25:99\"}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"FIXED\"}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"FIXED\",\"time\":\"7am\"}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"DYNAMIC\",\"zman\":\"NETZ\",\"offsetMinutes\":241}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"ROUNDED\",\"zman\":\"NETZ\",\"offsetMinutes\":-241}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"DYNAMIC\",\"zman\":\"NOT_A_ZMAN\",\"offsetMinutes\":0}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"DYNAMIC\",\"offsetMinutes\":0}}}",
                "{\"schedule\":{\"sunday\":{\"zman\":\"NETZ\"}}}",
                "{\"schedule\":{\"sunday\":{\"type\":\"WEEKLY\"}}}",
                "{\"schedule\":{\"someday\":{\"type\":\"NONE\"}}}",
                "{\"nusach\":\"MARS\"}",
                "{\"enabled\":false,\"locationId\":\"LOC-B1\"}",
                "{\"locationId\":\"NOPE\"}",
                "{\"locationId\":\"\"}",
                "{\"notes\":\"" + "x".repeat(300) + "\"}",
                "{\"enable\":false}",
                "{\"enabled\":\"maybe\"}",
                "not json");
        for (String body : badBodies) {
            var result = mvc.perform(patchMinyan(managerKey, body)).andReturn().getResponse();
            assertThat(result.getStatus()).as(body).isEqualTo(400);
            assertThat(result.getContentAsString()).as(body).contains("VALIDATION_ERROR");
        }

        Minyan stored = minyanim.findById(MINYAN_ID).orElseThrow();
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getStartTime1()).isEqualTo("T7:0:0:0");
        assertThat(stored.getLocationId()).isEqualTo("LOC-A1");
        assertThat(publicEventCount()).isPositive();
    }

    // --- authorization ---------------------------------------------------------------------------------

    @Test
    void requestsWithoutATokenAreUnauthenticated() throws Exception {
        mvc.perform(api(get(BASE + "/minyanim")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(api(get(BASE + "/minyanim/" + MINYAN_ID)))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(patch(BASE + "/minyanim/" + MINYAN_ID)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(api(patch(BASE)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        // Even for a shul that does not exist: no token, no information.
        mvc.perform(api(patch("/api/v1/organizations/nope")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keyWithoutTheScopeIsForbidden() throws Exception {
        String readOnly = createKey("manager", Set.of(ApiScope.SCHEDULE_READ), Set.of());
        mvc.perform(patchMinyan(readOnly, "{\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
        mvc.perform(api(patch(BASE)).header("Authorization", bearer(readOnly))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));

        String writeOnly = createKey("manager", Set.of(ApiScope.MINYANIM_WRITE), Set.of());
        mvc.perform(api(get(BASE + "/minyanim")).header("Authorization", bearer(writeOnly)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_SCOPE"));
        assertThat(minyanim.findById(MINYAN_ID).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void managerCannotReachAnotherShul() throws Exception {
        String url = "/api/v1/organizations/ORGB/minyanim";
        mvc.perform(api(get(url)).header("Authorization", bearer(managerKey)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(api(patch(url + "/" + OTHER_MINYAN_ID)).header("Authorization", bearer(managerKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(api(patch("/api/v1/organizations/ORGB")).header("Authorization", bearer(managerKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}"))
                .andExpect(status().isForbidden());
        assertThat(minyanim.findById(OTHER_MINYAN_ID).orElseThrow().isEnabled()).isTrue();
        assertThat(organizations.findById("ORGB").orElseThrow().getName()).isEqualTo("Shul B");
    }

    @Test
    void shulRestrictedKeyCannotReachOtherShuls() throws Exception {
        String restricted = createKey("superadmin",
                Set.of(ApiScope.SCHEDULE_READ, ApiScope.MINYANIM_WRITE, ApiScope.SHUL_WRITE), Set.of("ORGB"));
        mvc.perform(api(get(BASE + "/minyanim")).header("Authorization", bearer(restricted)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(patchMinyan(restricted, "{\"enabled\":false}")).andExpect(status().isForbidden());
        mvc.perform(api(patch(BASE)).header("Authorization", bearer(restricted))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        // ...but works on the shul it is restricted to.
        mvc.perform(api(get("/api/v1/organizations/ORGB/minyanim")).header("Authorization", bearer(restricted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void superAdminCanEditAnyShul() throws Exception {
        String superKey = createKey("superadmin", Set.of(ApiScope.MINYANIM_WRITE, ApiScope.SHUL_WRITE), Set.of());
        mvc.perform(api(patch("/api/v1/organizations/ORGB/minyanim/" + OTHER_MINYAN_ID)).header("Authorization", bearer(superKey))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    // --- shul profile ----------------------------------------------------------------------------------

    @Test
    void shulPatchUpdatesThePublicOrganization() throws Exception {
        mvc.perform(patchShul(managerKey,
                        "{\"name\":\"  Shul A Renamed \",\"address\":\"1 Main St, Teaneck, NJ\",\"websiteUrl\":\"example.org\","
                                + "\"nusach\":\"sefard\",\"color\":\"#112233\",\"slug\":\"Shul-A-New\",\"whatsapp\":\"https://chat.example/a\","
                                + "\"calendarUrl\":\"https://calendar.example.org/cal\",\"useScrapedCalendar\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ORGA"))
                .andExpect(jsonPath("$.data.name").value("Shul A Renamed"))
                .andExpect(jsonPath("$.data.slug").value("shul-a-new"))
                .andExpect(jsonPath("$.data.nusach").value("SEFARD"))
                .andExpect(jsonPath("$.data.calendarUrl").value("https://calendar.example.org/cal"))
                .andExpect(jsonPath("$.data.useScrapedCalendar").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mvc.perform(api(get("/api/v1/organizations/ORGA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Shul A Renamed"))
                .andExpect(jsonPath("$.data.slug").value("shul-a-new"))
                .andExpect(jsonPath("$.data.color").value("#112233"))
                .andExpect(jsonPath("$.data.nusach").value("SEFARD"))
                .andExpect(jsonPath("$.data.address").value("1 Main St, Teaneck, NJ"))
                .andExpect(jsonPath("$.data.websiteUrl").value("example.org"))
                .andExpect(jsonPath("$.data.whatsapp").value("https://chat.example/a"));
        mvc.perform(api(get("/api/v1/organizations/shul-a-new"))).andExpect(status().isOk());

        // Fields that are left out stay; an empty string clears.
        mvc.perform(patchShul(managerKey, "{\"address\":\"\",\"whatsapp\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Shul A Renamed"))
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.whatsapp").doesNotExist())
                .andExpect(jsonPath("$.data.websiteUrl").value("example.org"));
    }

    @Test
    void invalidShulPatchesAreRejectedAndChangeNothing() throws Exception {
        List<String> badBodies = List.of(
                "{\"name\":\"   \"}",
                "{\"name\":\"\"}",
                "{\"websiteUrl\":\"http://exa mple.org\"}",
                "{\"websiteUrl\":\"javascript:alert(1)\"}",
                "{\"slug\":\"shul-b\"}",
                "{\"slug\":\"Bad Slug!\"}",
                "{\"nusach\":\"MARS\"}",
                "{\"color\":\"red\"}",
                "{\"color\":\"#12\"}",
                "{\"calendarUrl\":\"file:///etc/passwd\"}",
                "{\"calendarUrl\":\"not a url\"}",
                "{\"name\":\"" + "x".repeat(300) + "\"}",
                "{\"nme\":\"Typo\"}");
        for (String body : badBodies) {
            var result = mvc.perform(patchShul(managerKey, body)).andReturn().getResponse();
            assertThat(result.getStatus()).as(body).isEqualTo(400);
            assertThat(result.getContentAsString()).as(body).contains("VALIDATION_ERROR");
        }
        Organization stored = organizations.findById("ORGA").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Shul A");
        assertThat(stored.getUrlSlug()).isEqualTo("shul-a");
        assertThat(stored.getNusach()).isEqualTo(Nusach.ASHKENAZ);
    }

    @Test
    void sendingTheShulsOwnSlugIsNotADuplicate() throws Exception {
        mvc.perform(patchShul(managerKey, "{\"slug\":\"shul-a\",\"name\":\"Shul A2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("shul-a"));
    }

    @Test
    void publicGetOnTheSamePathStillWorksAlongsidePatch() throws Exception {
        mvc.perform(api(get("/api/v1/organizations/ORGA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Shul A"));
    }

    // --- helpers ---------------------------------------------------------------------------------------

    private int publicEventCount() throws Exception {
        String today = LocalDate.now().toString();
        String json = mvc.perform(api(get("/api/v1/organizations/ORGA/schedule")).param("date", today))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<List<Object>>read(json, "$.data").size();
    }

    private void mockPublicTimes(String expectedTime) throws Exception {
        String today = LocalDate.now().toString();
        String json = mvc.perform(api(get("/api/v1/organizations/ORGA/schedule")).param("date", today))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> times = com.jayway.jsonpath.JsonPath.read(json, "$.data[*].startTime");
        assertThat(times).isNotEmpty().allMatch(expectedTime::equals);
    }

    private MockHttpServletRequestBuilder patchMinyan(String key, String body) {
        return api(patch(BASE + "/minyanim/" + MINYAN_ID)).header("Authorization", bearer(key))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder patchShul(String key, String body) {
        return api(patch(BASE)).header("Authorization", bearer(key))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String bearer(String key) {
        return "Bearer " + key;
    }

    private MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        // A fresh client IP per request keeps these tests clear of the rate limits.
        return builder.with(remote("10.8." + (ipCounter / 250) + "." + (ipCounter++ % 250 + 1)));
    }

    private static RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private String createKey(String owner, Set<ApiScope> scopes, Set<String> restriction) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null, List.of(new SimpleGrantedAuthority(Role.ADMIN.getName()))));
        try {
            String accountId = userRepository.findAll().stream()
                    .filter(u -> owner.equals(u.getUsername())).findFirst().orElseThrow().getId();
            return apiKeys.create(accountId, "test key", false, scopes, restriction).rawKey();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Organization shul(String id, String name, String slug) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setUrlSlug(slug);
        organization.setEnabled(true);
        organization.setOrgColor("#275ed8");
        organization.setNusach(Nusach.ASHKENAZ);
        return organization;
    }

    private static Minyan minyan(String id, String orgId, String locationId, String time) {
        return new Minyan(id, MinyanType.SHACHARIS, locationId, orgId, true,
                time, time, time, time, time, time, time, time, time, time, time,
                null, Nusach.ASHKENAZ, "#275ed8", null);
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
